package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.SwarmResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Swarm Secrets：敏感凭据注入服务任务，内容创建后不可读（Docker 设计如此）
 */
@RestController
@RequestMapping("/api/env/{envId}/swarm/secrets")
@RequiredArgsConstructor
public class SecretController {

    private final SwarmResourceService resourceService;

    public record SecretCreateRequest(String name, String data, Map<String, String> labels) { }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long envId) {
        return ApiResponse.success(resourceService.listSecrets(envId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Map<String, Object>> create(@PathVariable Long envId,
                                                   @RequestBody SecretCreateRequest req) {
        return ApiResponse.success(resourceService.createSecret(envId, req.name(), req.data(), req.labels()));
    }

    @DeleteMapping("/{secretId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> delete(@PathVariable Long envId, @PathVariable String secretId) {
        resourceService.deleteSecret(envId, secretId);
        return ApiResponse.success();
    }
}
