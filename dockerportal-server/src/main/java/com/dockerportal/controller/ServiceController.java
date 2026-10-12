package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.SwarmService;
import com.github.dockerjava.api.model.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/swarm/services")
@RequiredArgsConstructor
public class ServiceController {

    private final SwarmService swarmService;

    @GetMapping
    public ApiResponse<List<com.github.dockerjava.api.model.Service>> list(@PathVariable Long envId) {
        return ApiResponse.success(swarmService.listServices(envId));
    }

    @GetMapping("/{serviceId}")
    public ApiResponse<com.github.dockerjava.api.model.Service> inspect(@PathVariable Long envId,
                                                        @PathVariable String serviceId) {
        return ApiResponse.success(swarmService.inspectService(envId, serviceId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<String> create(@PathVariable Long envId,
                                       @RequestBody Map<String, Object> serviceDef) {
        return ApiResponse.success(swarmService.createService(envId, serviceDef));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> update(@PathVariable Long envId,
                                     @PathVariable String serviceId,
                                     @RequestBody Map<String, Object> updates) {
        swarmService.updateService(envId, serviceId, updates);
        return ApiResponse.success();
    }

    @PostMapping("/{serviceId}/scale")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> scale(@PathVariable Long envId,
                                    @PathVariable String serviceId,
                                    @RequestParam int replicas) {
        swarmService.scaleService(envId, serviceId, replicas);
        return ApiResponse.success();
    }

    @DeleteMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> remove(@PathVariable Long envId,
                                     @PathVariable String serviceId) {
        swarmService.removeService(envId, serviceId);
        return ApiResponse.success();
    }

    @GetMapping("/{serviceId}/tasks")
    public ApiResponse<List<Task>> tasks(@PathVariable Long envId,
                                          @PathVariable String serviceId) {
        return ApiResponse.success(swarmService.listServiceTasks(envId, serviceId));
    }

    @GetMapping("/{serviceId}/logs")
    public ApiResponse<String> logs(@PathVariable Long envId,
                                     @PathVariable String serviceId,
                                     @RequestParam(defaultValue = "100") int tail) {
        return ApiResponse.success(swarmService.getServiceLogs(envId, serviceId, tail));
    }
}
