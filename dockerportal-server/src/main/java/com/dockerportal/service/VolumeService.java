package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolumeService {

    private final DockerClientManager dockerClientManager;

    public ListVolumesResponse listVolumes(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listVolumesCmd().exec();
    }

    public InspectVolumeResponse inspectVolume(Long envId, String volumeName) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.inspectVolumeCmd(volumeName).exec();
    }

    public String createVolume(Long envId, String name, String driver, Map<String, String> labels) {
        DockerClient client = dockerClientManager.getClient(envId);
        var createCmd = client.createVolumeCmd()
                .withName(name);
        if (driver != null) {
            createCmd = createCmd.withDriver(driver);
        }
        if (labels != null) {
            createCmd = createCmd.withLabels(labels);
        }
        var response = createCmd.exec();
        log.info("Volume created: {}", response.getName());
        return response.getName();
    }

    public void removeVolume(Long envId, String volumeName, boolean force) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeVolumeCmd(volumeName).exec();
        log.info("Volume removed: {}", volumeName);
    }
}
