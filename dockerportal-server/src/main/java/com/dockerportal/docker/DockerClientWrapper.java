package com.dockerportal.docker;

import com.github.dockerjava.api.DockerClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;

@Slf4j
public class DockerClientWrapper implements Closeable {

    @Getter
    private final DockerClient client;

    @Getter
    private final Long environmentId;

    private volatile boolean connected;

    public DockerClientWrapper(DockerClient client, Long environmentId) {
        this.client = client;
        this.environmentId = environmentId;
        this.connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void testConnection() {
        try {
            client.pingCmd().exec();
            this.connected = true;
        } catch (Exception e) {
            this.connected = false;
            log.error("Docker connection test failed for environment {}: {}", environmentId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void close() {
        try {
            client.close();
            this.connected = false;
        } catch (Exception e) {
            log.warn("Error closing Docker client for environment {}: {}", environmentId, e.getMessage());
        }
    }
}
