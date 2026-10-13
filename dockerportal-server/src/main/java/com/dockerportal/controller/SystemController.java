package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.PruneService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Docker 系统级资源清理与磁盘占用
 */
@RestController
@RequestMapping("/api/env/{envId}/system")
@RequiredArgsConstructor
public class SystemController {

    private final PruneService pruneService;

    /** docker system df */
    @GetMapping("/df")
    public ApiResponse<Map<String, Object>> df(@PathVariable Long envId) {
        return ApiResponse.success(pruneService.systemDf(envId));
    }

    /** docker container prune */
    @PostMapping("/prune/containers")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "SYSTEM_PRUNE_CONTAINERS", resourceType = "system", level = AuditLevel.CRITICAL, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> pruneContainers(@PathVariable Long envId) {
        return ApiResponse.success(pruneService.pruneContainers(envId));
    }

    /** docker image prune [-a] */
    @PostMapping("/prune/images")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "SYSTEM_PRUNE_IMAGES", resourceType = "system", level = AuditLevel.CRITICAL, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> pruneImages(@PathVariable Long envId,
                                                        @RequestParam(defaultValue = "false") boolean all) {
        return ApiResponse.success(pruneService.pruneImages(envId, all));
    }

    /** docker volume prune */
    @PostMapping("/prune/volumes")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "SYSTEM_PRUNE_VOLUMES", resourceType = "system", level = AuditLevel.CRITICAL, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> pruneVolumes(@PathVariable Long envId) {
        return ApiResponse.success(pruneService.pruneVolumes(envId));
    }

    /** docker network prune */
    @PostMapping("/prune/networks")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "SYSTEM_PRUNE_NETWORKS", resourceType = "system", level = AuditLevel.CRITICAL, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> pruneNetworks(@PathVariable Long envId) {
        return ApiResponse.success(pruneService.pruneNetworks(envId));
    }

    /** docker builder prune */
    @PostMapping("/prune/builder")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "SYSTEM_PRUNE_BUILDER", resourceType = "system", level = AuditLevel.CRITICAL, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> pruneBuilder(@PathVariable Long envId) {
        return ApiResponse.success(pruneService.pruneBuilder(envId));
    }

    /** docker system prune [-a] */
    @PostMapping("/prune/system")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "SYSTEM_PRUNE_SYSTEM", resourceType = "system", level = AuditLevel.CRITICAL, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> pruneSystem(@PathVariable Long envId,
                                                        @RequestParam(defaultValue = "false") boolean all) {
        return ApiResponse.success(pruneService.pruneSystem(envId, all));
    }
}
