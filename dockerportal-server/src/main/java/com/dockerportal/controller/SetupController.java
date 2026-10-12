package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.dto.SetupRequest;
import com.dockerportal.model.dto.SetupStatusDTO;
import com.dockerportal.service.SetupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首次安装向导的匿名入口，SecurityConfig 里显式 permitAll。
 * 建号成功后系统即进入已初始化状态，这两个接口不会再有第二种可用行为。
 */
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public ApiResponse<SetupStatusDTO> status() {
        return ApiResponse.success(setupService.getStatus());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> initialize(@Valid @RequestBody SetupRequest request,
                                                       HttpServletRequest http) {
        return ApiResponse.success(setupService.initialize(request, http));
    }
}
