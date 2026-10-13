package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.entity.Registry;
import com.dockerportal.service.RegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 镜像仓库地址与账号管理（全局配置，不区分 Docker 环境）
 */
@RestController
@RequestMapping("/api/registries")
@RequiredArgsConstructor
public class RegistryController {

    private final RegistryService registryService;

    @GetMapping
    public ApiResponse<List<Registry>> list() {
        return ApiResponse.success(registryService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "REGISTRY_CREATE", resourceType = "registry", level = AuditLevel.SECURITY, resourceId = "#result?.data?.id")
    public ApiResponse<Registry> create(@RequestBody Registry registry) {
        return ApiResponse.success(registryService.create(registry));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "REGISTRY_UPDATE", resourceType = "registry", level = AuditLevel.SECURITY, resourceId = "#id")
    public ApiResponse<Registry> update(@PathVariable Long id, @RequestBody Registry registry) {
        return ApiResponse.success(registryService.update(id, registry));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "REGISTRY_DELETE", resourceType = "registry", level = AuditLevel.CRITICAL, resourceId = "#id")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        registryService.delete(id);
        return ApiResponse.success();
    }

    /** 测试已保存配置的连通性 */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> test(@PathVariable Long id) {
        return ApiResponse.success(Map.of("message", registryService.testConnection(id)));
    }

    /** 用表单值直接探测（未保存也可测试） */
    @PostMapping("/test")
    public ApiResponse<Map<String, String>> testWith(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "");
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        return ApiResponse.success(Map.of("message", registryService.testConnectionWith(url, username, password)));
    }
}
