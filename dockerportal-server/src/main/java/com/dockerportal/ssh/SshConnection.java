package com.dockerportal.ssh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * SSH 连接封装 - 管理单个 SSH 会话和通道
 */
@Slf4j
@Getter
public class SshConnection implements Closeable {

    private final ClientSession session;
    private final ClientChannel channel;
    private final String nodeId;
    private final long createdAt;

    public SshConnection(ClientSession session, ClientChannel channel, String nodeId) {
        this.session = session;
        this.channel = channel;
        this.nodeId = nodeId;
        this.createdAt = System.currentTimeMillis();
    }

    public InputStream getOutputStream() {
        return channel.getInvertedOut();
    }

    public InputStream getErrorStream() {
        return channel.getInvertedErr();
    }

    public OutputStream getInputStream() {
        return channel.getInvertedIn();
    }

    public boolean isOpen() {
        return session.isOpen() && channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            log.warn("Error closing SSH channel: {}", e.getMessage());
        }
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.warn("Error closing SSH session: {}", e.getMessage());
        }
        log.info("SSH connection closed for node: {}", nodeId);
    }
}
