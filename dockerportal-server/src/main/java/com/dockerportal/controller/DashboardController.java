package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.vo.DashboardVO;
import com.dockerportal.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/env/{envId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardVO> getDashboard(@PathVariable Long envId) {
        return ApiResponse.success(dashboardService.getDashboard(envId));
    }
}
