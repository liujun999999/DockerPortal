package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.SwarmResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Swarm Configs：配置文件注入服务任务，内容可读可改（改 = 删除重建）
 */
@RestController
@RequestMapping("/api/env/{envId}/swarm/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final SwarmResourceService resourceService;

    public record ConfigCreateRequest(String name, String data, Map<String, String> labels, Boolean templatable) { }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long envId) {
        return ApiResponse.success(resourceService.listConfigs(envId));
    }

    @GetMapping("/{configId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long envId, @PathVariable String configId) {
        return ApiResponse.success(resourceService.getConfig(envId, configId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Map<String, Object>> create(@PathVariable Long envId,
                                                   @RequestBody ConfigCreateRequest req) {
        return ApiResponse.success(resourceService.createConfig(envId, req.name(), req.data(),
                req.labels(), req.templatable()));
    }

    @DeleteMapping("/{configId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> delete(@PathVariable Long envId, @PathVariable String configId) {
        resourceService.deleteConfig(envId, configId);
        return ApiResponse.success();
    }
}
