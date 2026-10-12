package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Network;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkService {

    private final DockerClientManager dockerClientManager;

    public List<Network> listNetworks(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listNetworksCmd().exec();
    }

    public Network inspectNetwork(Long envId, String networkId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.inspectNetworkCmd().withNetworkId(networkId).exec();
    }

    public String createNetwork(Long envId, String name, String driver, boolean internal, boolean attachable) {
        DockerClient client = dockerClientManager.getClient(envId);
        var response = client.createNetworkCmd()
                .withName(name)
                .withDriver(driver != null ? driver : "bridge")
                .withInternal(internal)
                .withAttachable(attachable)
                .exec();
        log.info("Network created: {} ({})", name, response.getId());
        return response.getId();
    }

    public void removeNetwork(Long envId, String networkId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeNetworkCmd(networkId).exec();
        log.info("Network removed: {}", networkId);
    }

    public void connectContainer(Long envId, String networkId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.connectToNetworkCmd()
                .withNetworkId(networkId)
                .withContainerId(containerId)
                .exec();
        log.info("Container {} connected to network {}", containerId, networkId);
    }

    public void disconnectContainer(Long envId, String networkId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.disconnectFromNetworkCmd()
                .withNetworkId(networkId)
                .withContainerId(containerId)
                .exec();
        log.info("Container {} disconnected from network {}", containerId, networkId);
    }
}
