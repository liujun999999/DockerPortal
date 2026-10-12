package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping("/node")
    public ApiResponse<Map<String, Object>> nodeInfo(@PathVariable Long envId) {
        return ApiResponse.success(monitorService.getNodeInfo(envId));
    }

    @GetMapping("/containers")
    public ApiResponse<List<Map<String, Object>>> containerStats(@PathVariable Long envId) {
        return ApiResponse.success(monitorService.getAllContainerStats(envId));
    }

    @GetMapping("/containers/{containerId}")
    public ApiResponse<Map<String, Object>> containerStat(@PathVariable Long envId,
                                                           @PathVariable String containerId) {
        return ApiResponse.success(monitorService.getContainerStats(envId, containerId));
    }
}
