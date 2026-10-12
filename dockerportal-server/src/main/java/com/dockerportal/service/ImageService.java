package com.dockerportal.service;

import com.dockerportal.docker.DockerClientFactory;
import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.exception.BusinessException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final DockerClientManager dockerClientManager;
    private final RegistryService registryService;
    private final DockerClientFactory dockerClientFactory;
    private final EnvironmentService environmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Image> listImages(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listImagesCmd().exec();
    }

    public void pullImage(Long envId, String imageName, String tag) {
        pullImage(envId, imageName, tag, null);
    }

    public void pullImage(Long envId, String imageName, String tag, Long registryId) {
        DockerClient client = dockerClientManager.getClient(envId);
        try {
            PullImageCmd pullCmd = client.pullImageCmd(imageName + ":" + tag);
            // 命中已配置的镜像仓库时自动携带凭据，可拉取私有仓库镜像；
            // 前端显式选了仓库 id 则优先用它（id=0 代表内置 Docker Hub 匿名，不携凭据）。
            com.github.dockerjava.api.model.AuthConfig auth = (registryId != null)
                    ? registryService.buildAuthConfigById(registryId)
                    : registryService.resolveAuthConfig(envId, imageName);
            if (auth != null) {
                pullCmd.withAuthConfig(auth);
                log.info("Pulling {} with registry credentials of [{}]", imageName, auth.getRegistryAddress());
            }
            pullCmd.exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Image pulled: {}:{}", imageName, tag);
    }

    /**
     * 推送镜像到仓库：先把 imageId 打上新目标 tag（与 docker CLI 习习惯一致：push 前需已 tag），再推送。
     * registryId=0/不传且镜像 host 未命中已配置仓库时挑 Docker Hub 匿名（需仓库公开/daemon 已登录）。
     */
    public void pushImage(Long envId, String imageId, String repo, String tag, Long registryId) {
        if (repo == null || repo.isBlank() || tag == null || tag.isBlank()) {
            throw new BusinessException(40000, "推送目标仓库与 tag 不能为空");
        }
        DockerClient client = dockerClientManager.getClient(envId);
        client.tagImageCmd(imageId, repo.trim(), tag.trim()).exec();

        AuthConfig auth = (registryId != null)
                ? registryService.buildAuthConfigById(registryId)
                : registryService.resolveAuthConfig(envId, repo);
        var cmd = client.pushImageCmd(repo.trim() + ":" + tag.trim());
        if (auth != null) {
            cmd.withAuthConfig(auth);
            log.info("Pushing {}:{} with registry credentials of [{}]", repo, tag, auth.getRegistryAddress());
        }
        // 推送失败（denied/unauthorized）在响应流里而非异常，需收集上报
        final String[] error = {null};
        try {
            cmd.exec(new ResultCallback.Adapter<PushResponseItem>() {
                @Override
                public void onNext(PushResponseItem item) {
                    if (item != null && item.getError() != null && !item.getError().isBlank()) {
                        error[0] = item.getError();
                    }
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(50000, "推送被中断");
        } catch (Exception e) {
            throw new BusinessException(40002, "推送失败：" + e.getMessage());
        }
        if (error[0] != null) {
            throw new BusinessException(40002, "推送失败：" + error[0]);
        }
        log.info("Image pushed: {}:{}", repo, tag);
    }

    /** 引用方列表最多列几个，剩下的用「等 N 个」收尾 */
    private static final int MAX_REF_SHOWN = 5;

    public void removeImage(Long envId, String imageId) {
        DockerClient client = dockerClientManager.getClient(envId);
        assertImageUnused(client, imageId);
        client.removeImageCmd(imageId).exec();
        log.info("Image removed: {}", imageId);
    }

    /**
     * 使用中的镜像一律不给删，也不提供强制删除：强删被容器引用的镜像只会把容器
     * 变成无法重建的孤儿（重启时 No such image），现场排查时也没法再 run 出同样的环境。
     * 容器按镜像 ID 匹配（含已停止的），Swarm 服务按镜像名匹配（副本数为 0 也算在用）。
     */
    private void assertImageUnused(DockerClient client, String imageId) {
        String canonicalId;
        List<String> repoTags;
        try {
            var inspect = client.inspectImageCmd(imageId).exec();
            canonicalId = inspect.getId();
            repoTags = inspect.getRepoTags() == null ? List.of() : inspect.getRepoTags();
        } catch (Exception e) {
            // 连 inspect 都取不到，交给 daemon 按原有方式报错，不在这里多包一层
            return;
        }

        List<Container> using = client.listContainersCmd().withShowAll(true).exec().stream()
                .filter(c -> canonicalId != null && canonicalId.equals(c.getImageId()))
                .toList();
        if (!using.isEmpty()) {
            String names = using.stream().map(this::containerLabel).limit(MAX_REF_SHOWN).reduce((a, b) -> a + "、" + b).orElse("");
            String more = using.size() > MAX_REF_SHOWN ? " 等 " + using.size() + " 个" : "";
            throw new BusinessException(40900, "镜像正被 " + using.size() + " 个容器使用（" + names + more
                    + "），请先删除这些容器再删镜像；已停止的容器也会占用镜像。");
        }

        // 未启用 Swarm 的环境上这个调用会失败，服务引用只是额外一道锁，取不到就跳过
        List<com.github.dockerjava.api.model.Service> services;
        try {
            services = client.listServicesCmd().exec();
        } catch (Exception e) {
            return;
        }
        List<String> hitServices = new ArrayList<>();
        for (com.github.dockerjava.api.model.Service s : services) {
            var spec = s.getSpec();
            String ref = spec == null || spec.getTaskTemplate() == null || spec.getTaskTemplate().getContainerSpec() == null
                    ? null : spec.getTaskTemplate().getContainerSpec().getImage();
            if (ref != null && serviceRefMatches(ref, repoTags, canonicalId)) {
                hitServices.add(spec.getName() == null ? s.getId() : spec.getName());
            }
        }
        if (!hitServices.isEmpty()) {
            String names = hitServices.stream().limit(MAX_REF_SHOWN).reduce((a, b) -> a + "、" + b).orElse("");
            String more = hitServices.size() > MAX_REF_SHOWN ? " 等 " + hitServices.size() + " 个" : "";
            throw new BusinessException(40900, "镜像正被 Swarm 服务引用（" + names + more
                    + "），请先下线相关服务，或把服务改指新镜像并重新部署后再删镜像。");
        }
    }

    private String containerLabel(Container c) {
        String name = c.getNames() == null || c.getNames().length == 0 ? c.getId() : c.getNames()[0];
        return name.replaceFirst("^/", "") + "（" + c.getState() + "）";
    }

    /** 服务里的镜像引用可能是 name:tag、name:tag@digest 或只写 name（由 daemon 按 latest 解析） */
    private boolean serviceRefMatches(String ref, List<String> repoTags, String imageId) {
        String namePart = ref;
        int at = ref.indexOf('@');
        if (at >= 0) {
            namePart = ref.substring(0, at);
            if (ref.substring(at + 1).equals(imageId)) {
                return true;
            }
        }
        if (repoTags.contains(namePart)) {
            return true;
        }
        // 判断有没有 tag 要看最后一段，镜像名里的仓库端口也带冒号（harbor:8443/nginx）
        String lastSeg = namePart.substring(namePart.lastIndexOf('/') + 1);
        return !lastSeg.contains(":") && repoTags.contains(namePart + ":latest");
    }

    public void tagImage(Long envId, String imageId, String repo, String tag) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.tagImageCmd(imageId, repo, tag).exec();
        log.info("Image tagged: {} -> {}:{}", imageId, repo, tag);
    }

    public com.github.dockerjava.api.command.InspectImageResponse inspectImage(Long envId, String imageId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.inspectImageCmd(imageId).exec();
    }

    /** 镜像分层历史：docker-java 3.3.6 无 history 命令，直调 daemon GET /images/{ref}/history */
    public List<Map<String, Object>> getImageHistory(Long envId, String imageId) {
        var env = environmentService.getEntity(envId);
        ApacheDockerHttpClient http = dockerClientFactory.createHttpClient(env);
        try {
            String ref = URLEncoder.encode(imageId, StandardCharsets.UTF_8);
            var req = DockerHttpClient.Request.builder()
                    .method(DockerHttpClient.Request.Method.GET)
                    .path("/v1.44/images/" + ref + "/history")
                    .build();
            try (DockerHttpClient.Response resp = http.execute(req)) {
                String body = resp.getBody() == null ? ""
                        : new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                if (resp.getStatusCode() >= 400) {
                    String msg = body;
                    try {
                        JsonNode n = objectMapper.readTree(body);
                        if (n.hasNonNull("message")) msg = n.get("message").asText();
                    } catch (Exception ignored) { }
                    throw new BusinessException(40002, "获取镜像分层失败：" + msg);
                }
                List<Map<String, Object>> out = new ArrayList<>();
                for (JsonNode node : objectMapper.readTree(body)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("Id", node.path("Id").asText(null));
                    m.put("Created", node.path("Created").asLong(0) * 1000);
                    m.put("CreatedBy", collapseSpaces(node.path("CreatedBy").asText("")));
                    m.put("Size", node.path("Size").asLong(0));
                    m.put("Comment", node.path("Comment").asText(""));
                    out.add(m);
                }
                return out;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(40002, "获取镜像分层失败：" + e.getMessage());
        } finally {
            try { http.close(); } catch (Exception ignored) { }
        }
    }

    /** Dockerfile 指令常跨多行（尾部 \ + 换行缩进），压成单行便于表格展示 */
    private String collapseSpaces(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
