package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.entity.AlertEvent;
import com.dockerportal.model.entity.AlertRule;
import com.dockerportal.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // ===== 告警规则 =====

    @GetMapping("/rules")
    public ApiResponse<List<AlertRule>> listRules(
            @RequestParam(required = false) Long envId) {
        if (envId != null) {
            return ApiResponse.success(alertService.listRulesByEnv(envId));
        }
        return ApiResponse.success(alertService.listRules());
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<AlertRule> getRule(@PathVariable Long id) {
        return ApiResponse.success(alertService.getRule(id));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AlertRule> createRule(@RequestBody AlertRule rule) {
        return ApiResponse.success(alertService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AlertRule> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        return ApiResponse.success(alertService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return ApiResponse.success();
    }

    @PostMapping("/rules/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> toggleRule(@PathVariable Long id) {
        alertService.toggleRule(id);
        return ApiResponse.success();
    }

    // ===== 告警事件 =====

    @GetMapping("/events")
    public ApiResponse<Page<AlertEvent>> listEvents(
            @RequestParam(required = false) Long envId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(alertService.listEvents(envId, page, size));
    }

    @GetMapping("/events/rule/{ruleId}")
    public ApiResponse<List<AlertEvent>> eventsByRule(@PathVariable Long ruleId) {
        return ApiResponse.success(alertService.listEventsByRule(ruleId));
    }
}
