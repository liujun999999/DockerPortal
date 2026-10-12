package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监控服务 - 采集容器和节点指标数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final DockerClientManager dockerClientManager;

    /**
     * 获取容器实时统计信息
     */
    public Map<String, Object> getContainerStats(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 使用一次性 stats 采集（非流式）
            final Map<String, Object> statsData = new LinkedHashMap<>();
            client.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics stats) {
                            statsData.putAll(parseStatistics(stats));
                        }
                    })
                    .awaitCompletion();
            result.putAll(statsData);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.put("error", "Stats collection interrupted");
        } catch (Exception e) {
            result.put("error", "Failed to collect stats: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取所有运行中容器的统计概览
     */
    public List<Map<String, Object>> getAllContainerStats(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        List<Container> running = client.listContainersCmd().withShowAll(false).exec();
        List<Map<String, Object>> statsList = new ArrayList<>();

        for (Container container : running) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("id", container.getId());
            stats.put("name", container.getNames() != null && container.getNames().length > 0
                    ? container.getNames()[0].replaceFirst("/", "") : container.getId().substring(0, 12));
            stats.put("image", container.getImage());
            stats.put("state", container.getState());
            stats.put("status", container.getStatus());

            // 尝试获取资源使用
            try {
                final Map<String, Object> parsed = new LinkedHashMap<>();
                client.statsCmd(container.getId())
                        .withNoStream(true)
                        .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics>() {
                            @Override
                            public void onNext(Statistics s) {
                                parsed.putAll(parseStatistics(s));
                            }
                        })
                        .awaitCompletion();
                stats.putAll(parsed);
            } catch (Exception e) {
                stats.put("cpuPercent", 0.0);
                stats.put("memoryUsage", 0L);
                stats.put("memoryLimit", 0L);
                stats.put("memoryPercent", 0.0);
            }
            statsList.add(stats);
        }

        return statsList;
    }

    /**
     * 获取节点级别信息
     */
    public Map<String, Object> getNodeInfo(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        var info = client.infoCmd().exec();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("os", info.getOperatingSystem());
        result.put("arch", info.getArchitecture());
        result.put("cpus", info.getNCPU());
        result.put("memory", info.getMemTotal());
        result.put("dockerVersion", info.getServerVersion());
        result.put("kernelVersion", info.getKernelVersion());
        result.put("containers", info.getContainers());
        result.put("containersRunning", info.getContainersRunning());
        result.put("containersStopped", info.getContainersStopped());
        result.put("images", info.getImages());
        return result;
    }

    private Map<String, Object> parseStatistics(Statistics stats) {
        Map<String, Object> result = new LinkedHashMap<>();

        // CPU
        try {
            var cpuStats = stats.getCpuStats();
            var precpuStats = stats.getPreCpuStats();
            if (cpuStats != null && precpuStats != null) {
                long cpuDelta = cpuStats.getCpuUsage().getTotalUsage() - precpuStats.getCpuUsage().getTotalUsage();
                long systemDelta = cpuStats.getSystemCpuUsage() - precpuStats.getSystemCpuUsage();
                long numCpus = cpuStats.getOnlineCpus() != null ? cpuStats.getOnlineCpus() : 1;
                double cpuPercent = systemDelta > 0 ? (cpuDelta * 1.0 / systemDelta) * numCpus * 100.0 : 0.0;
                result.put("cpuPercent", Math.round(cpuPercent * 100.0) / 100.0);
            }
        } catch (Exception e) {
            result.put("cpuPercent", 0.0);
        }

        // Memory
        try {
            var memoryStats = stats.getMemoryStats();
            if (memoryStats != null) {
                long usage = memoryStats.getUsage() != null ? memoryStats.getUsage() : 0;
                long limit = memoryStats.getLimit() != null ? memoryStats.getLimit() : 1;
                result.put("memoryUsage", usage);
                result.put("memoryLimit", limit);
                result.put("memoryPercent", Math.round((usage * 100.0 / limit) * 100.0) / 100.0);
            }
        } catch (Exception e) {
            result.put("memoryUsage", 0L);
            result.put("memoryLimit", 0L);
            result.put("memoryPercent", 0.0);
        }

        // Network
        try {
            var networks = stats.getNetworks();
            if (networks != null && !networks.isEmpty()) {
                long rxBytes = 0, txBytes = 0;
                for (var entry : networks.values()) {
                    rxBytes += entry.getRxBytes() != null ? entry.getRxBytes() : 0;
                    txBytes += entry.getTxBytes() != null ? entry.getTxBytes() : 0;
                }
                result.put("networkRxBytes", rxBytes);
                result.put("networkTxBytes", txBytes);
            }
        } catch (Exception e) {
            result.put("networkRxBytes", 0L);
            result.put("networkTxBytes", 0L);
        }

        // Block IO
        try {
            var blkioStats = stats.getBlkioStats();
            if (blkioStats != null) {
                long readBytes = 0, writeBytes = 0;
                if (blkioStats.getIoServiceBytesRecursive() != null) {
                    for (var entry : blkioStats.getIoServiceBytesRecursive()) {
                        if ("Read".equals(entry.getOp())) readBytes += entry.getValue();
                        if ("Write".equals(entry.getOp())) writeBytes += entry.getValue();
                    }
                }
                result.put("blockReadBytes", readBytes);
                result.put("blockWriteBytes", writeBytes);
            }
        } catch (Exception e) {
            result.put("blockReadBytes", 0L);
            result.put("blockWriteBytes", 0L);
        }

        return result;
    }
}
