package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.model.vo.DashboardVO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.command.ListVolumesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DockerClientManager dockerClientManager;

    public DashboardVO getDashboard(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);

        // Containers
        List<Container> allContainers = client.listContainersCmd().withShowAll(true).exec();
        long running = allContainers.stream()
                .filter(c -> "running".equals(c.getState()))
                .count();
        long stopped = allContainers.size() - running;

        // Images
        List<Image> images = client.listImagesCmd().exec();

        // Networks
        List<Network> networks = client.listNetworksCmd().exec();

        // Volumes
        ListVolumesResponse volumes = client.listVolumesCmd().exec();

        // Swarm info
        var info = client.infoCmd().exec();
        boolean swarmMode = info.getSwarm() != null
                && info.getSwarm().getLocalNodeState() != null
                && info.getSwarm().getLocalNodeState() != com.github.dockerjava.api.model.LocalNodeState.INACTIVE;

        int swarmNodes = 0;
        int swarmServices = 0;
        int swarmTasks = 0;

        if (swarmMode) {
            try {
                swarmNodes = client.listSwarmNodesCmd().exec().size();
                swarmServices = client.listServicesCmd().exec().size();
                swarmTasks = client.listTasksCmd().exec().size();
            } catch (Exception e) {
                log.warn("Failed to get Swarm info: {}", e.getMessage());
            }
        }

        return DashboardVO.builder()
                .runningContainers((int) running)
                .stoppedContainers((int) stopped)
                .totalImages(images.size())
                .totalNetworks(networks.size())
                .totalVolumes(volumes.getVolumes() != null ? volumes.getVolumes().size() : 0)
                .swarmMode(swarmMode)
                .swarmNodes(swarmNodes)
                .swarmServices(swarmServices)
                .swarmTasks(swarmTasks)
                .build();
    }
}
