package com.dockerportal.docker;

import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.entity.Environment;
import com.dockerportal.model.enums.ConnectionStatus;
import com.dockerportal.repository.EnvironmentRepository;
import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerClientManager {

    private final DockerClientFactory clientFactory;
    private final EnvironmentRepository environmentRepository;
    private final ConcurrentHashMap<Long, DockerClientWrapper> clients = new ConcurrentHashMap<>();

    public DockerClientWrapper getOrCreate(Environment environment) {
        return clients.compute(environment.getId(), (id, existing) -> {
            if (existing != null && existing.isConnected()) {
                return existing;
            }
            if (existing != null) {
                existing.close();
            }
            DockerClient client = clientFactory.createClient(environment);
            DockerClientWrapper wrapper = new DockerClientWrapper(client, environment.getId());
            try {
                wrapper.testConnection();
                log.info("Docker client created for environment: {} ({})", environment.getName(), environment.getId());
            } catch (Exception e) {
                wrapper.close();
                throw new RuntimeException("Failed to connect to Docker: " + e.getMessage(), e);
            }
            return wrapper;
        });
    }

    public DockerClient getClient(Long environmentId) {
        DockerClientWrapper wrapper = clients.get(environmentId);
        if (wrapper == null || !wrapper.isConnected()) {
            // 懒连接：内存中无可用连接时，从数据库读取环境配置并建立连接
            wrapper = lazyConnect(environmentId);
        }
        return wrapper.getClient();
    }

    private DockerClientWrapper lazyConnect(Long environmentId) {
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new BusinessException(40400, "环境不存在: " + environmentId));
        try {
            DockerClientWrapper wrapper = getOrCreate(environment);
            // 同步数据库状态，避免下拉框显示与实际连接状态不一致
            if (environment.getStatus() != ConnectionStatus.CONNECTED) {
                environment.setStatus(ConnectionStatus.CONNECTED);
                environment.setErrorMessage(null);
                environmentRepository.save(environment);
            }
            return wrapper;
        } catch (Exception e) {
            environment.setStatus(ConnectionStatus.ERROR);
            environment.setErrorMessage(e.getMessage());
            environmentRepository.save(environment);
            throw new BusinessException(50001, "连接 Docker 环境失败: " + e.getMessage());
        }
    }

    public DockerClientWrapper getWrapper(Long environmentId) {
        DockerClientWrapper wrapper = clients.get(environmentId);
        if (wrapper == null) {
            throw new BusinessException(40400, "环境未连接或不存在");
        }
        return wrapper;
    }

    public void remove(Long environmentId) {
        DockerClientWrapper wrapper = clients.remove(environmentId);
        if (wrapper != null) {
            wrapper.close();
            log.info("Docker client removed for environment: {}", environmentId);
        }
    }

    public void refresh(Environment environment) {
        remove(environment.getId());
        getOrCreate(environment);
    }

    public boolean isConnected(Long environmentId) {
        DockerClientWrapper wrapper = clients.get(environmentId);
        return wrapper != null && wrapper.isConnected();
    }
}
