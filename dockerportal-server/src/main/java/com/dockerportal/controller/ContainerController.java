package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.dto.ContainerCreateRequest;
import com.dockerportal.model.dto.FileWriteRequest;
import com.dockerportal.service.ContainerFileService;
import com.dockerportal.service.ContainerService;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.InspectContainerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;
    private final ContainerFileService containerFileService;

    @GetMapping
    public ApiResponse<List<Container>> list(@PathVariable Long envId,
                                             @RequestParam(defaultValue = "true") boolean showAll) {
        return ApiResponse.success(containerService.listContainers(envId, showAll));
    }

    @GetMapping("/{containerId}")
    public ApiResponse<InspectContainerResponse> inspect(@PathVariable Long envId,
                                                         @PathVariable String containerId) {
        return ApiResponse.success(containerService.inspectContainer(envId, containerId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "CONTAINER_CREATE", resourceType = "container", level = AuditLevel.OPERATION, resourceId = "#result?.data", environmentId = "#envId")
    public ApiResponse<String> create(@PathVariable Long envId,
                                      @Valid @RequestBody ContainerCreateRequest request) {
        return ApiResponse.success(containerService.createContainer(envId, request));
    }

    @PostMapping("/{containerId}/{action}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "CONTAINER_ACTION", resourceType = "container", level = AuditLevel.OPERATION, resourceId = "#containerId", environmentId = "#envId")
    public ApiResponse<Void> action(@PathVariable Long envId,
                                    @PathVariable String containerId,
                                    @PathVariable String action) {
        switch (action) {
            case "start" -> containerService.startContainer(envId, containerId);
            case "stop" -> containerService.stopContainer(envId, containerId);
            case "restart" -> containerService.restartContainer(envId, containerId);
            case "pause" -> containerService.pauseContainer(envId, containerId);
            case "unpause" -> containerService.unpauseContainer(envId, containerId);
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ApiResponse.success();
    }

    @DeleteMapping("/{containerId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "CONTAINER_REMOVE", resourceType = "container", level = AuditLevel.CRITICAL, resourceId = "#containerId", environmentId = "#envId")
    public ApiResponse<Void> remove(@PathVariable Long envId,
                                    @PathVariable String containerId,
                                    @RequestParam(defaultValue = "false") boolean force) {
        containerService.removeContainer(envId, containerId, force);
        return ApiResponse.success();
    }

    @PostMapping("/{containerId}/rename")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "CONTAINER_RENAME", resourceType = "container", level = AuditLevel.OPERATION, resourceId = "#containerId", environmentId = "#envId")
    public ApiResponse<Void> rename(@PathVariable Long envId,
                                    @PathVariable String containerId,
                                    @RequestParam String name) {
        containerService.renameContainer(envId, containerId, name);
        return ApiResponse.success();
    }

    /** docker update：热调整内存/CPU 限制，留空字段不修改 */
    @PutMapping("/{containerId}/resources")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "CONTAINER_UPDATE", resourceType = "container", level = AuditLevel.CONFIG, resourceId = "#containerId", environmentId = "#envId")
    public ApiResponse<Void> updateResources(@PathVariable Long envId,
                                             @PathVariable String containerId,
                                             @RequestBody Map<String, Object> body) {
        containerService.updateResources(envId, containerId, body);
        return ApiResponse.success();
    }

    @GetMapping("/{containerId}/logs")
    public ApiResponse<String> logs(@PathVariable Long envId,
                                    @PathVariable String containerId,
                                    @RequestParam(defaultValue = "100") int tail,
                                    @RequestParam(defaultValue = "true") boolean timestamps) {
        return ApiResponse.success(containerService.getContainerLogs(envId, containerId, tail, timestamps));
    }

    /**
     * 列出容器内目录
     */
    @GetMapping("/{containerId}/files")
    public ApiResponse<List<Map<String, Object>>> listFiles(@PathVariable Long envId,
                                                            @PathVariable String containerId,
                                                            @RequestParam(defaultValue = "/") String path) {
        return ApiResponse.success(containerFileService.listDir(envId, containerId, path));
    }

    /**
     * 读取容器内文本文件
     */
    @GetMapping("/{containerId}/files/content")
    public ApiResponse<Map<String, Object>> readFile(@PathVariable Long envId,
                                                     @PathVariable String containerId,
                                                     @RequestParam String path) {
        return ApiResponse.success(containerFileService.readFile(envId, containerId, path));
    }

    /**
     * 写入（覆盖）容器内文本文件
     */
    @PutMapping("/{containerId}/files/content")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "CONTAINER_FILE_WRITE", resourceType = "container", level = AuditLevel.OPERATION, resourceId = "#containerId", environmentId = "#envId")
    public ApiResponse<Void> writeFile(@PathVariable Long envId,
                                       @PathVariable String containerId,
                                       @RequestParam String path,
                                       @RequestBody FileWriteRequest body) {
        containerFileService.writeFile(envId, containerId, path, body.getContent());
        return ApiResponse.success();
    }
}
