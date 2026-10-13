package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.entity.SshConfig;
import com.dockerportal.service.SshService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/env/{envId}/ssh")
@RequiredArgsConstructor
public class SshTerminalController {

    private final SshService sshService;

    @GetMapping("/configs")
    public ApiResponse<List<SshConfig>> listConfigs(@PathVariable Long envId) {
        return ApiResponse.success(sshService.listConfigs(envId));
    }

    @GetMapping("/configs/{id}")
    public ApiResponse<SshConfig> getConfig(@PathVariable Long envId, @PathVariable Long id) {
        return ApiResponse.success(sshService.getConfig(id));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "SSH_CONFIG_CREATE", resourceType = "ssh-config", level = AuditLevel.SECURITY, resourceId = "#result?.data?.id", environmentId = "#envId")
    public ApiResponse<SshConfig> createConfig(@PathVariable Long envId, @RequestBody SshConfig config) {
        config.setEnvironmentId(envId);
        return ApiResponse.success(sshService.createConfig(config));
    }

    /** 测试 SSH 连通性（不落库），凭据留空且带 id 时沿用已保存配置 */
    @PostMapping("/configs/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> testConfig(@PathVariable Long envId, @RequestBody SshConfig config) {
        config.setEnvironmentId(envId);
        return ApiResponse.success(sshService.testConnection(config));
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "SSH_CONFIG_UPDATE", resourceType = "ssh-config", level = AuditLevel.SECURITY, resourceId = "#id", environmentId = "#envId")
    public ApiResponse<SshConfig> updateConfig(@PathVariable Long envId,
                                                @PathVariable Long id,
                                                @RequestBody SshConfig config) {
        return ApiResponse.success(sshService.updateConfig(id, config));
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "SSH_CONFIG_DELETE", resourceType = "ssh-config", level = AuditLevel.CRITICAL, resourceId = "#id", environmentId = "#envId")
    public ApiResponse<Void> deleteConfig(@PathVariable Long envId, @PathVariable Long id) {
        sshService.deleteConfig(id);
        return ApiResponse.success();
    }
}
