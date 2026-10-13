package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.dto.EnvironmentCreateRequest;
import com.dockerportal.model.dto.EnvironmentDTO;
import com.dockerportal.service.EnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @GetMapping
    public ApiResponse<List<EnvironmentDTO>> list() {
        return ApiResponse.success(environmentService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<EnvironmentDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(environmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ENV_CREATE", resourceType = "environment", level = AuditLevel.SECURITY, resourceId = "#result?.data?.name")
    public ApiResponse<EnvironmentDTO> create(@Valid @RequestBody EnvironmentCreateRequest request) {
        return ApiResponse.success(environmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ENV_UPDATE", resourceType = "environment", level = AuditLevel.SECURITY, resourceId = "#id")
    public ApiResponse<EnvironmentDTO> update(@PathVariable Long id,
                                              @Valid @RequestBody EnvironmentCreateRequest request) {
        return ApiResponse.success(environmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ENV_DELETE", resourceType = "environment", level = AuditLevel.CRITICAL, resourceId = "#id")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        environmentService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> test(@RequestBody EnvironmentCreateRequest request) {
        return ApiResponse.success(environmentService.testConnection(request));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> testById(@PathVariable Long id) {
        return ApiResponse.success(environmentService.testConnection(id));
    }

    @PostMapping("/{id}/connect")
    @Auditable(action = "ENV_CONNECT", resourceType = "environment", level = AuditLevel.OPERATION, resourceId = "#id")
    public ApiResponse<EnvironmentDTO> connect(@PathVariable Long id) {
        return ApiResponse.success(environmentService.connect(id));
    }

    @PostMapping("/{id}/disconnect")
    @Auditable(action = "ENV_DISCONNECT", resourceType = "environment", level = AuditLevel.OPERATION, resourceId = "#id")
    public ApiResponse<EnvironmentDTO> disconnect(@PathVariable Long id) {
        return ApiResponse.success(environmentService.disconnect(id));
    }
}
