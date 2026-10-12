package com.dockerportal.service;

import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.entity.Registry;
import com.dockerportal.repository.RegistryRepository;
import com.github.dockerjava.api.model.AuthConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryService {

    /** 内置默认仓库 Docker Hub 的虚拟 id（不会与 JPA IDENTITY 自增主键碰车）*/
    public static final long DOCKER_HUB_BUILTIN_ID = 0L;

    private final RegistryRepository registryRepository;

    /** 列表：首位为内置 Docker Hub（无需配置即可访问公开镜像），后续为用户自行添加的仓库凭据 */
    public List<Registry> list() {
        List<Registry> result = new ArrayList<>();
        result.add(dockerHubBuiltin());
        List<Registry> db = registryRepository.findAll();
        db.forEach(r -> r.setPassword(null));
        result.addAll(db);
        return result;
    }

    /** 内置 Docker Hub 行：不落库、不携定任何凭据；当用户已额外保存 docker.io 凭据时，拉取仍会命中那些行 */
    public static Registry dockerHubBuiltin() {
        Registry r = new Registry();
        r.setId(DOCKER_HUB_BUILTIN_ID);
        r.setName("Docker Hub");
        r.setUrl("");
        r.setUsername("");
        r.setPassword(null);
        r.setNamespacePrefix("library");
        r.setBuiltin(true);
        return r;
    }

    /** 根据前端选中的仓库 id 直接取凭据（内置 Docker Hub id=0 或匿名仓库返回 null，代表匿名拉取）*/
    public AuthConfig buildAuthConfigById(Long registryId) {
        if (registryId == null || registryId == DOCKER_HUB_BUILTIN_ID) return null;
        Registry r = registryRepository.findById(registryId)
                .orElseThrow(() -> new BusinessException(40400, "镜像仓库配置不存在: " + registryId));
        if (r.getUsername() == null || r.getUsername().isBlank()) return null; // 匿名仓库
        String host = normalizeUrl(r.getUrl());
        if (host.isEmpty()) host = "index.docker.io";
        String address = hostMatches("index.docker.io", host) ? "https://index.docker.io/v1/" : host;
        return new AuthConfig()
                .withRegistryAddress(address)
                .withUsername(r.getUsername())
                .withPassword(r.getPassword());
    }

    public Registry create(Registry registry) {
        validate(registry, true);
        registry.setId(null);
        Registry saved = registryRepository.save(registry);
        saved.setPassword(null);
        return saved;
    }

    public Registry update(Long id, Registry incoming) {
        Registry db = registryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "镜像仓库配置不存在: " + id));
        db.setName(incoming.getName());
        db.setUrl(normalizeUrl(incoming.getUrl()));
        db.setUsername(incoming.getUsername());
        // 密码留空表示不修改
        if (incoming.getPassword() != null && !incoming.getPassword().isBlank()) {
            db.setPassword(incoming.getPassword());
        }
        db.setNamespacePrefix(incoming.getNamespacePrefix());
        db.setEnvironmentId(incoming.getEnvironmentId());
        validate(db, false);
        Registry saved = registryRepository.save(db);
        saved.setPassword(null);
        return saved;
    }

    public void delete(Long id) {
        registryRepository.deleteById(id);
    }

    /**
     * 测试仓库连通性：GET {scheme}://{host}/v2/（Basic 认证）。
     * 私有仓库规范：未授权返回 401/403 也算连通且凭据被识别；2xx 表示凭据有效。
     */
    public String testConnection(Long id) {
        Registry db = registryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "镜像仓库配置不存在: " + id));
        return probe(db.getUrl(), db.getUsername(), db.getPassword());
    }

    /** 用表单值直接探测（尚未保存的配置也可测试） */
    public String testConnectionWith(String url, String username, String password) {
        return probe(url, username, password);
    }

    private String probe(String rawUrl, String username, String password) {
        // normalizeUrl 已保证结果为 host[:port]，无路径/协议
        String host = normalizeUrl(rawUrl);
        if (host.isBlank()) host = "registry-1.docker.io";
        String endpoint = "https://" + host + "/v2/";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .GET();
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder().encodeToString(
                        (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return "连接成功，账号凭据有效（HTTP " + code + "）";
            } else if (code == 401 || code == 403) {
                boolean anonymous = username == null || username.isBlank();
                return anonymous
                        ? "仓库可访问（匿名探测返回 HTTP " + code + "）：部分公开镜像库的 /v2/ 端点也要求登录，不影响匿名拉取公开镜像"
                        : "仓库可访问，但当前账号未通过认证（HTTP " + code + "），请检查用户名/密码或项目权限";
            } else if (code == 404) {
                return "仓库可访问，但 /v2/ 端点不存在（HTTP 404），请确认地址是镜像仓库而非项目页面";
            }
            return "仓库返回异常状态码 HTTP " + code;
        } catch (java.net.UnknownHostException e) {
            throw new BusinessException(50010, "无法解析域名 " + host + "，请检查仓库地址是否正确、本机 DNS 是否可用");
        } catch (HttpTimeoutException e) {
            throw new BusinessException(50010, "连接 " + host + " 超时，请检查网络或仓库端口是否开放");
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessException(50010, "连接仓库 " + host + " 失败：" + detail);
        }
    }

    /**
     * 根据镜像引用匹配可用凭据（全局配置 + 指定环境的专属配置）。
     * 匹配不到返回 null（匿名拉取）。
     */
    public AuthConfig resolveAuthConfig(Long envId, String imageRef) {
        String host = registryHostOf(imageRef);
        List<Registry> candidates = new ArrayList<>(registryRepository.findByEnvironmentIdIsNullOrderByCreatedAtDesc());
        if (envId != null) {
            candidates.addAll(registryRepository.findByEnvironmentIdOrderByCreatedAtDesc(envId));
        }
        for (Registry r : candidates) {
            String rHost = normalizeUrl(r.getUrl());
            // 未填地址视为 Docker Hub 凭据
            if (rHost.isEmpty()) rHost = "index.docker.io";
            if (hostMatches(host, rHost)) {
                // 匿名仓库：命中地址但不携凭据
                if (r.getUsername() == null || r.getUsername().isBlank()) return null;
                String address = hostMatches("index.docker.io", rHost) ? "https://index.docker.io/v1/" : rHost;
                return new AuthConfig()
                        .withRegistryAddress(address)
                        .withUsername(r.getUsername())
                        .withPassword(r.getPassword());
            }
        }
        return null;
    }

    /**
     * 按 Docker 语法规则解析镜像引用所属仓库 host：
     * 首段含 "." 或 ":" 或是 "localhost" 时视为 registry，否则为 Docker Hub。
     */
    static String registryHostOf(String imageRef) {
        int slash = imageRef.indexOf('/');
        if (slash > 0) {
            String first = imageRef.substring(0, slash);
            if (first.contains(".") || first.contains(":") || "localhost".equals(first)) {
                return first;
            }
        }
        return "index.docker.io";
    }

    static boolean hostMatches(String imageHost, String registryHost) {
        if (imageHost.equals(registryHost)) return true;
        // Docker Hub 多种写法归一：index.docker.io / registry.hub.docker.com / docker.io
        boolean hubImage = imageHost.equals("index.docker.io") || imageHost.equals("registry.hub.docker.com") || imageHost.equals("docker.io");
        boolean hubRegistry = registryHost.equals("index.docker.io") || registryHost.equals("registry.hub.docker.com") || registryHost.equals("docker.io");
        return hubImage && hubRegistry;
    }

    /** 去掉协议前缀与尾部斜杠，只保留 host[:port] */
    static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        u = u.replaceFirst("^https?://", "");
        // 剥离粘贴自 docker login 输出的 user:pass@ 前缀
        int at = u.lastIndexOf('@');
        if (at >= 0) u = u.substring(at + 1);
        u = u.replaceAll("/+$", "");
        // 用户可能粘贴完整镜像地址，截掉路径部分
        int slash = u.indexOf('/');
        if (slash > 0) u = u.substring(0, slash);
        return u;
    }

    private void validate(Registry r, boolean requirePassword) {
        if (r.getName() == null || r.getName().isBlank()) {
            throw new BusinessException(40000, "仓库名称不能为空");
        }
        r.setUrl(normalizeUrl(r.getUrl()));
        // 匿名仓库：用户名/密码均可不填（如 docker.xuanyuan.me 这类免登录公开镜像库），空白归一为空串
        String username = r.getUsername() == null ? "" : r.getUsername().trim();
        r.setUsername(username);
        if (username.isEmpty()) {
            // 空串而非 null：兼容老库残留的 NOT NULL 约束（ddl-auto:update 不会去掉）
            r.setPassword("");
            return;
        }
        boolean hasPassword = r.getPassword() != null && !r.getPassword().isBlank();
        if (requirePassword && !hasPassword) {
            throw new BusinessException(40000, "填写了用户名，密码/访问令牌不能为空；匿名仓库请两者留空");
        }
    }
}
