package com.dockerportal.service;

import com.dockerportal.docker.DockerClientFactory;
import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.docker.DockerClientWrapper;
import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.dto.EnvironmentCreateRequest;
import com.dockerportal.model.dto.EnvironmentDTO;
import com.dockerportal.model.entity.Environment;
import com.dockerportal.model.enums.ConnectionStatus;
import com.dockerportal.repository.EnvironmentRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final DockerClientManager dockerClientManager;
    private final DockerClientFactory dockerClientFactory;

    public List<EnvironmentDTO> listAll() {
        return environmentRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public EnvironmentDTO getById(Long id) {
        Environment env = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "环境不存在"));
        return toDTO(env);
    }

    public EnvironmentDTO create(EnvironmentCreateRequest request) {
        Environment env = Environment.builder()
                .name(request.getName())
                .type(request.getType())
                .endpoint(request.getEndpoint())
                .tlsEnabled(request.getTlsEnabled())
                .tlsCaCert(request.getTlsCaCert())
                .tlsCert(request.getTlsCert())
                .tlsKey(request.getTlsKey())
                .sshUser(request.getSshUser())
                .sshKey(request.getSshKey())
                .build();
        env = environmentRepository.save(env);
        log.info("Environment created: {} ({})", env.getName(), env.getId());
        return toDTO(env);
    }

    public EnvironmentDTO update(Long id, EnvironmentCreateRequest request) {
        Environment env = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "环境不存在"));

        env.setName(request.getName());
        env.setType(request.getType());
        env.setEndpoint(request.getEndpoint());
        env.setTlsEnabled(request.getTlsEnabled());
        env.setTlsCaCert(request.getTlsCaCert());
        env.setTlsCert(request.getTlsCert());
        env.setTlsKey(request.getTlsKey());
        env.setSshUser(request.getSshUser());
        env.setSshKey(request.getSshKey());

        // Disconnect if was connected
        dockerClientManager.remove(id);
        env.setStatus(ConnectionStatus.DISCONNECTED);

        env = environmentRepository.save(env);
        log.info("Environment updated: {} ({})", env.getName(), env.getId());
        return toDTO(env);
    }

    public void delete(Long id) {
        dockerClientManager.remove(id);
        environmentRepository.deleteById(id);
        log.info("Environment deleted: {}", id);
    }

    public EnvironmentDTO connect(Long id) {
        Environment env = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "环境不存在"));

        try {
            DockerClientWrapper wrapper = dockerClientManager.getOrCreate(env);
            DockerClient client = wrapper.getClient();

            // Get Docker info
            Info info = client.infoCmd().exec();
            env.setDockerVersion(info.getServerVersion() != null ? info.getServerVersion() : "unknown");
            env.setSwarmMode(info.getSwarm() != null
                    && info.getSwarm().getLocalNodeState() != null
                    && info.getSwarm().getLocalNodeState() != com.github.dockerjava.api.model.LocalNodeState.INACTIVE);
            env.setStatus(ConnectionStatus.CONNECTED);
            env.setErrorMessage(null);
        } catch (Exception e) {
            env.setStatus(ConnectionStatus.ERROR);
            env.setErrorMessage(e.getMessage());
            log.error("Failed to connect to environment {}: {}", id, e.getMessage());
        }

        env = environmentRepository.save(env);
        return toDTO(env);
    }

    public EnvironmentDTO disconnect(Long id) {
        Environment env = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "环境不存在"));

        dockerClientManager.remove(id);
        env.setStatus(ConnectionStatus.DISCONNECTED);
        env = environmentRepository.save(env);
        return toDTO(env);
    }

    public Environment getEntity(Long id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "环境不存在"));
    }

    /**
     * 测试连接（不落库、不影响已保存环境状态）：临时建连取 info/version，返回引擎概况。
     * 失败时抛 BusinessException 携带可读原因。
     */
    public Map<String, Object> testConnection(EnvironmentCreateRequest request) {
        Environment draft = Environment.builder()
                .name(request.getName() == null || request.getName().isBlank() ? "tmp" : request.getName())
                .type(request.getType())
                .endpoint(request.getEndpoint())
                .tlsEnabled(request.getTlsEnabled())
                .tlsCaCert(request.getTlsCaCert())
                .tlsCert(request.getTlsCert())
                .tlsKey(request.getTlsKey())
                .sshUser(request.getSshUser())
                .sshKey(request.getSshKey())
                .build();
        return probe(draft);
    }

    /** 测试已保存环境的连通性 */
    public Map<String, Object> testConnection(Long id) {
        return probe(getEntity(id));
    }

    private Map<String, Object> probe(Environment env) {
        DockerClient client = null;
        try {
            client = dockerClientFactory.createClient(env);
            Info info = client.infoCmd().exec();
            var version = client.versionCmd().exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dockerVersion", version.getVersion());
            result.put("apiVersion", version.getApiVersion());
            result.put("os", version.getOperatingSystem());
            result.put("arch", version.getArch());
            result.put("kernelVersion", version.getKernelVersion());
            result.put("ncpu", info.getNCPU());
            result.put("memGB", info.getMemTotal() == null ? null
                    : Math.round(info.getMemTotal() / 1024.0 / 1024.0 / 1024.0 * 10) / 10.0);
            result.put("containerCount", info.getContainers());
            String swarmState = info.getSwarm() != null && info.getSwarm().getLocalNodeState() != null
                    ? String.valueOf(info.getSwarm().getLocalNodeState()).toLowerCase() : "inactive";
            result.put("swarmState", swarmState);
            return result;
        } catch (Exception e) {
            log.warn("Environment connection test failed for {}: {}", env.getName(), e.toString());
            throw new BusinessException(40002, "连接失败：" + rootMessage(e));
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) { }
            }
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        java.util.Set<Throwable> seen = new java.util.HashSet<>();
        while (cur.getCause() != null && seen.add(cur.getCause())) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private EnvironmentDTO toDTO(Environment env) {
        return EnvironmentDTO.builder()
                .id(env.getId())
                .name(env.getName())
                .type(env.getType())
                .endpoint(env.getEndpoint())
                .tlsEnabled(env.getTlsEnabled())
                .status(env.getStatus())
                .swarmMode(env.getSwarmMode())
                .dockerVersion(env.getDockerVersion())
                .errorMessage(env.getErrorMessage())
                .createdAt(env.getCreatedAt())
                .updatedAt(env.getUpdatedAt())
                .build();
    }
}
