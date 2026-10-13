package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.StackService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/swarm/stacks")
@RequiredArgsConstructor
public class StackController {

    private final StackService stackService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long envId) {
        return ApiResponse.success(stackService.listStacks(envId));
    }

    @GetMapping("/{stackName}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long envId,
                                                 @PathVariable String stackName) {
        return ApiResponse.success(stackService.getStack(envId, stackName));
    }

    /** 取回部署时记录的 compose 原文，供「编辑 Stack」回填 */
    @GetMapping("/{stackName}/compose")
    public ApiResponse<Map<String, Object>> compose(@PathVariable Long envId,
                                                    @PathVariable String stackName) {
        return ApiResponse.success(stackService.getCompose(envId, stackName));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "STACK_DEPLOY", resourceType = "stack", level = AuditLevel.OPERATION, environmentId = "#envId")
    public ApiResponse<Map<String, Object>> deploy(@PathVariable Long envId,
                                                   @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String compose = body.get("composeContent");
        return ApiResponse.success(stackService.deployStack(envId, name, compose));
    }

    @DeleteMapping("/{stackName}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "STACK_REMOVE", resourceType = "stack", level = AuditLevel.CRITICAL, resourceId = "#stackName", environmentId = "#envId")
    public ApiResponse<Void> remove(@PathVariable Long envId,
                                     @PathVariable String stackName) {
        stackService.removeStack(envId, stackName);
        return ApiResponse.success();
    }
}
