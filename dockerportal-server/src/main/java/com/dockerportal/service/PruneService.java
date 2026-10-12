package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PruneCmd;
import com.github.dockerjava.api.model.PruneResponse;
import com.github.dockerjava.api.model.PruneType;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker 资源清理（prune）与磁盘占用（system df）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PruneService {

    private final DockerClientManager dockerClientManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 删除计数相关的响应字段（不同 prune 类型字段名不同，统一从原始 payload 取） */
    private static final String[] DELETED_LIST_KEYS = {
            "ContainersDeleted", "ImagesDeleted", "NetworksDeleted",
            "VolumesDeleted", "LayersDeleted", "CachesDeleted",
    };

    public Map<String, Object> pruneContainers(Long envId) {
        return doPrune(envId, PruneType.CONTAINERS, null);
    }

    /** all=false 等价 docker image prune（仅悬空），all=true 等价 docker image prune -a（所有未被容器使用的） */
    public Map<String, Object> pruneImages(Long envId, boolean all) {
        return doPrune(envId, PruneType.IMAGES, all ? null : Boolean.TRUE);
    }

    public Map<String, Object> pruneVolumes(Long envId) {
        return doPrune(envId, PruneType.VOLUMES, null);
    }

    public Map<String, Object> pruneNetworks(Long envId) {
        return doPrune(envId, PruneType.NETWORKS, null);
    }

    public Map<String, Object> pruneBuilder(Long envId) {
        return doPrune(envId, PruneType.BUILD, null);
    }

    /**
     * 等价 docker system prune：容器 + 网络 + 悬空(或全部未用)镜像 + 构建缓存，按固定顺序执行并聚合结果
     */
    public Map<String, Object> pruneSystem(Long envId, boolean all) {
        Map<String, Object> containers = doPrune(envId, PruneType.CONTAINERS, null);
        Map<String, Object> networks = doPrune(envId, PruneType.NETWORKS, null);
        Map<String, Object> images = pruneImages(envId, all);
        Map<String, Object> builder = doPrune(envId, PruneType.BUILD, null);
        long space = 0;
        int deleted = 0;
        Map<String, Object> details = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : Map.of(
                "containers", containers, "networks", networks,
                "images", images, "builder", builder).entrySet()) {
            space += ((Number) e.getValue().get("spaceReclaimed")).longValue();
            deleted += ((Number) e.getValue().get("deletedCount")).intValue();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceReclaimed", space);
        result.put("deletedCount", deleted);
        details.put("containers", containers.get("deletedCount"));
        details.put("networks", networks.get("deletedCount"));
        details.put("images", images.get("deletedCount"));
        details.put("builder", builder.get("deletedCount"));
        result.put("details", details);
        return result;
    }

    private Map<String, Object> doPrune(Long envId, PruneType type, Boolean dangling) {
        DockerClient client = dockerClientManager.getClient(envId);
        PruneCmd cmd = client.pruneCmd(type);
        if (dangling != null) {
            cmd.withDangling(dangling);
        }
        PruneResponse resp = cmd.exec();
        long space = resp.getSpaceReclaimed() == null ? 0 : resp.getSpaceReclaimed();
        Map<String, Object> raw = resp.getRawValues();
        int deleted = 0;
        for (String key : DELETED_LIST_KEYS) {
            Object v = raw.get(key);
            if (v instanceof List<?> list) deleted += list.size();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceReclaimed", space);
        result.put("deletedCount", deleted);
        log.info("Prune {} -> deleted {} items, reclaimed {} bytes", type, deleted, space);
        return result;
    }

    /**
     * docker system df：docker-java 3.3.6 无对应命令，直接走底层 HTTP 调 /system/df。
     * 返回的是原始明细（Images/Containers/Volumes/BuildCache + LayersSize），总数、活跃、
     * 可回收这些汇总值由调用方按 docker CLI 的口径计算。
     * <p>
     * size=1 是给老 daemon（API &lt; 1.42）看的：那时容器可写层大小与数据卷用量都要显式索取，
     * 不传就干脆不返回（Volumes 甚至没有 UsageData）；新版 daemon 已经默认计算并忽略该参数。
     * 代价是 daemon 侧要真的去 du 一遍数据卷和快照层，所以这个接口不适合高频轮询。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> systemDf(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        if (!(client instanceof DockerClientImpl impl)) {
            throw new BusinessException(50002, "当前 Docker 客户端不支持原生 API 调用");
        }
        String apiVersion = impl.versionCmd().exec().getApiVersion();
        DockerHttpClient.Request request = DockerHttpClient.Request.builder()
                .method("GET")
                .path("/v" + apiVersion + "/system/df?size=1")
                .build();
        try (DockerHttpClient.Response response = impl.getHttpClient().execute(request)) {
            if (response.getStatusCode() != 200) {
                throw new BusinessException(50002, "docker system df 调用失败：HTTP " + response.getStatusCode());
            }
            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(50002, "docker system df 调用失败：" + e.getMessage(), e);
        }
    }
}
