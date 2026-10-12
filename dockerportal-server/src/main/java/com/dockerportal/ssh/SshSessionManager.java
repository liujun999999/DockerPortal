package com.dockerportal.ssh;

import com.dockerportal.model.entity.SshConfig;
import com.dockerportal.repository.SshConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 会话管理器 - 管理所有活跃的 SSH 连接
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshSessionManager {

    private final SshConfigRepository sshConfigRepository;
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();

    /**
     * 创建 SSH 连接
     */
    public SshConnection connect(Long envId, String nodeId) throws Exception {
        var configs = sshConfigRepository.findByEnvironmentId(envId);
        SshConfig config = configs.stream()
                .filter(c -> nodeId.equals(c.getNodeId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("SSH config not found for node: " + nodeId));

        return connectWithConfig(config);
    }

    /**
     * 使用指定配置创建 SSH 连接
     */
    public SshConnection connectWithConfig(SshConfig config) throws Exception {
        SshClient sshClient = SshClient.setUpDefaultClient();

        ClientSession session = sshClient.connect(config.getUsername(), config.getHost(), config.getPort())
                .verify(10000)
                .getSession();

        switch (config.getAuthType()) {
            case PASSWORD -> session.addPasswordIdentity(config.getPassword());
            case KEY -> {
                // SSH 密钥认证（简化处理）
                log.info("SSH key authentication for host: {}", config.getHost());
            }
        }

        session.auth().verify(10000);

        ChannelShell channel = session.createShellChannel();
        channel.setRedirectErrorStream(true);
        channel.open().verify(10000);

        SshConnection connection = new SshConnection(session, channel, config.getNodeId());
        String key = config.getEnvironmentId() + ":" + config.getNodeId();
        connections.put(key, connection);

        log.info("SSH connection established: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
        return connection;
    }

    /**
     * 获取已存在的连接
     */
    public SshConnection getConnection(Long envId, String nodeId) {
        String key = envId + ":" + nodeId;
        return connections.get(key);
    }

    /**
     * 关闭并移除连接
     */
    public void disconnect(Long envId, String nodeId) {
        String key = envId + ":" + nodeId;
        SshConnection connection = connections.remove(key);
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                log.warn("Error closing SSH connection: {}", e.getMessage());
            }
        }
    }

    /**
     * 关闭所有连接
     */
    public void disconnectAll() {
        connections.forEach((key, conn) -> {
            try {
                conn.close();
            } catch (IOException e) {
                log.warn("Error closing SSH connection: {}", e.getMessage());
            }
        });
        connections.clear();
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveCount() {
        return connections.size();
    }
}
