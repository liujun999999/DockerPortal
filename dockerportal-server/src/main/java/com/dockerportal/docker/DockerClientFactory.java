package com.dockerportal.docker;

import com.dockerportal.model.entity.Environment;
import com.dockerportal.model.enums.EnvironmentType;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class DockerClientFactory {

    public DockerClient createClient(Environment environment) {
        String dockerHost = resolveDockerHost(environment);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        return DockerClientImpl.getInstance(config, createHttpClient(environment));
    }

    /**
     * 单独暴露传输层客户端：供 docker-java 类型化 API 未覆盖的底层调用（如 config create 带 data）直接使用。
     * 调用方负责 close()。
     */
    public ApacheDockerHttpClient createHttpClient(Environment environment) {
        String dockerHost = resolveDockerHost(environment);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        // 故意不设 responseTimeout：httpclient5 把它实现成连接上的 socket 读超时，且对 exec /
        // 日志这类 101 升级后的 hijacked 长连接不会在读完响应头后复原，只要容器安静满 N 秒，
        // 读线程就抛 SocketTimeoutException 把会话掉线（实测 3s 配置在 +3040ms 断开）。
        // 卡死的 daemon 由 connectionTimeout 兜住 TCP/TLS 建连阶段，maxConnections 兜住并发上界。
        return new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String resolveDockerHost(Environment environment) {
        return switch (environment.getType()) {
            case LOCAL -> environment.getEndpoint().isEmpty()
                    ? "unix:///var/run/docker.sock"
                    : environment.getEndpoint();
            case TCP -> environment.getEndpoint();
            case SSH -> "ssh://" + environment.getSshUser() + "@" + environment.getEndpoint();
        };
    }
}
