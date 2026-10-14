package com.dockerportal.service;

import com.dockerportal.docker.DockerClientFactory;
import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.exception.BusinessException;
import com.dockerportal.exception.SwarmErrorClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Secret;
import com.github.dockerjava.api.model.SecretSpec;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Swarm 敏感数据管理：Secrets 与 Configs。
 * Secret：创建/列表/删除；内容写入后不可从 API 读取（Docker 设计如此，与 CLI 一致），轮换 = 新建 + 更新服务引用。
 * Config：创建/列表/详情（含内容）/删除；docker-java 的 ConfigSpec 缺失 data 字段，
 * 创建走底层 HTTP（DockerHttpClient），列表复用类型化 inspect（daemon 会返回 config 内容）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwarmResourceService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]*$");

    private final DockerClientManager dockerClientManager;
    private final DockerClientFactory dockerClientFactory;
    private final EnvironmentService environmentService;
    private final SwarmErrorClassifier swarmErrorClassifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Secrets ====================

    public List<Map<String, Object>> listSecrets(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Secret s : client.listSecretsCmd().exec()) {
            out.add(secretToView(s));
        }
        return out;
    }

    public Map<String, Object> createSecret(Long envId, String name, String dataPlain, Map<String, String> labels) {
        validateName(name);
        if (dataPlain == null || dataPlain.isEmpty()) {
            throw new BusinessException(40000, "Secret 内容不能为空");
        }
        DockerClient client = dockerClientManager.getClient(envId);
        requireManager(client);
        SecretSpec spec = new SecretSpec()
                .withName(name.trim())
                .withData(Base64.getEncoder().encodeToString(dataPlain.getBytes(StandardCharsets.UTF_8)));
        if (labels != null && !labels.isEmpty()) spec.withLabels(labels);
        var resp = client.createSecretCmd(spec).exec();
        log.info("Secret created: {} ({})", name, resp.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", resp.getId());
        out.put("name", name.trim());
        return out;
    }

    public void deleteSecret(Long envId, String secretId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeSecretCmd(secretId).exec();
        log.info("Secret deleted: {}", secretId);
    }

    private Map<String, Object> secretToView(Secret s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getSpec() != null ? s.getSpec().getName() : null);
        // Date.toString() 前端无法解析，统一转 ISO-8601（与 config 的原始字符串一致）
        m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toInstant().toString() : null);
        m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toInstant().toString() : null);
        m.put("labels", s.getSpec() != null ? s.getSpec().getLabels() : null);
        return m;
    }

    // ==================== Configs ====================

    /** 列表附带内容：daemon 的 /configs 列表接口会返回 Spec.Data，类型化模型丢字段，故走底层 HTTP */
    public List<Map<String, Object>> listConfigs(Long envId) {
        String json = rawRequest(envId, DockerHttpClient.Request.Method.GET, "/configs", null);
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode node : arr) {
                out.add(configNodeToView(node, true));
            }
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50000, "解析 config 列表失败: " + e.getMessage());
        }
    }

    public Map<String, Object> getConfig(Long envId, String configId) {
        // 类型化 inspect 先行校验存在性，再走 HTTP 拿内容
        DockerClient client = dockerClientManager.getClient(envId);
        client.inspectConfigCmd(configId).exec();
        String json = rawRequest(envId, DockerHttpClient.Request.Method.GET, "/configs/" + configId, null);
        try {
            return configNodeToView(objectMapper.readTree(json), true);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50000, "解析 config 失败: " + e.getMessage());
        }
    }

    public Map<String, Object> createConfig(Long envId, String name, String dataPlain,
                                           Map<String, String> labels, Boolean templatable) {
        validateName(name);
        if (dataPlain == null) throw new BusinessException(40000, "Config 内容不能为空");
        DockerClient client = dockerClientManager.getClient(envId);
        requireManager(client);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Name", name.trim());
        body.put("Data", Base64.getEncoder().encodeToString(dataPlain.getBytes(StandardCharsets.UTF_8)));
        if (labels != null && !labels.isEmpty()) body.put("Labels", labels);
        if (Boolean.TRUE.equals(templatable)) body.put("Templatable", true);
        try {
            String resp = rawRequest(envId, DockerHttpClient.Request.Method.POST,
                    "/configs/create?name=" + name.trim(), objectMapper.writeValueAsString(body));
            JsonNode node = objectMapper.readTree(resp);
            String id = node.get("ID").asText();
            log.info("Config created: {} ({})", name, id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("name", name.trim());
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50000, "创建 config 失败: " + e.getMessage());
        }
    }

    public void deleteConfig(Long envId, String configId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeConfigCmd(configId).exec();
        log.info("Config deleted: {}", configId);
    }

    private Map<String, Object> configNodeToView(JsonNode node, boolean withData) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", node.path("ID").asText(null));
        JsonNode spec = node.path("Spec");
        m.put("name", spec.path("Name").asText(null));
        m.put("createdAt", node.path("CreatedAt").asText(null));
        m.put("updatedAt", node.path("UpdatedAt").asText(null));
        Map<String, String> labels = new LinkedHashMap<>();
        spec.path("Labels").fields().forEachRemaining(f -> labels.put(f.getKey(), f.getValue().asText()));
        m.put("labels", labels);
        m.put("size", spec.path("Data").asText("").length());
        if (withData) {
            String data = spec.path("Data").asText("");
            try {
                m.put("data", data.isEmpty() ? "" : new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
            } catch (Exception e) {
                m.put("data", null);
            }
        }
        return m;
    }

    // ==================== 底层 HTTP ====================

    private String rawRequest(Long envId, DockerHttpClient.Request.Method method, String path, String bodyJson) {
        var env = environmentService.getEntity(envId);
        ApacheDockerHttpClient http = dockerClientFactory.createHttpClient(env);
        try {
            var builder = DockerHttpClient.Request.builder()
                    .method(method)
                    .path(apiPath(envId, path));
            if (bodyJson != null) {
                builder.putHeader("Content-Type", "application/json");
                builder.bodyBytes(bodyJson.getBytes(StandardCharsets.UTF_8));
            }
            try (DockerHttpClient.Response resp = http.execute(builder.build())) {
                String body = readAll(resp);
                if (resp.getStatusCode() >= 400) {
                    String message = extractMessage(body);
                    // Swarm 控制面错误（如 Worker 节点无管理权限的 503）映射为引导码，交由前端 SwarmGuide 渲染，
                    // 而不是把 Docker 原始报错直接弹成红错；与 docker-java 类型化命令路径保持一致。
                    if (swarmErrorClassifier.isSwarmError(message)) {
                        int code = swarmErrorClassifier.classify(envId, message);
                        throw new BusinessException(code, swarmErrorClassifier.friendlyMessage(code));
                    }
                    throw new BusinessException(50000, "Docker API " + resp.getStatusCode() + ": " + message);
                }
                return body;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(40002, "调用 Docker API 失败: " + e.getMessage());
        } finally {
            try { http.close(); } catch (Exception ignored) { }
        }
    }

    /** 与 docker-java 一致的版本协商：API_VERSION 环境变量 > 1.44 上限 */
    private String apiPath(Long envId, String path) {
        String version = System.getenv("API_VERSION");
        if (version == null || version.isBlank() || version.compareTo("1.44") > 0) version = "1.44";
        return "/v" + version + path;
    }

    private String readAll(DockerHttpClient.Response resp) throws Exception {
        try (InputStream in = resp.getBody()) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractMessage(String body) {
        try {
            JsonNode n = objectMapper.readTree(body);
            if (n.hasNonNull("message")) return n.get("message").asText();
        } catch (Exception ignored) { }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    // ==================== 公共校验 ====================

    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new BusinessException(40000, "名称只允许字母、数字、连字符、下划线和点，且以字母或数字开头");
        }
    }

    /** Secret/Config 增删只能在 Swarm Manager 上执行，非管理节点给出明确提示 */
    private void requireManager(DockerClient client) {
        try {
            var swarm = client.infoCmd().exec().getSwarm();
            var state = swarm == null ? null : swarm.getLocalNodeState();
            boolean control = swarm != null && Boolean.TRUE.equals(swarm.getControlAvailable());
            if (state != com.github.dockerjava.api.model.LocalNodeState.ACTIVE || !control) {
                throw new BusinessException(40002, "Secrets/Configs 仅在 Swarm 管理节点可用（当前节点状态: "
                        + (state == null ? "inactive" : String.valueOf(state).toLowerCase())
                        + "，控制面: " + (control ? "available" : "unavailable") + "）");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(40002, "无法确认 Swarm 角色: " + e.getMessage());
        }
    }
}
