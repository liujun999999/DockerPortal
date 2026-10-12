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
@RequestMapping("/api/env/{envId}/swarm")
@RequiredArgsConstructor
public class SwarmController {

    private final SwarmService swarmService;

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(@PathVariable Long envId) {
        return ApiResponse.success(swarmService.getSwarmInfo(envId));
    }

    /**
     * 获取节点加入集群的令牌与命令（仅 Manager 可查）
     */
    @GetMapping("/join-info")
    public ApiResponse<Map<String, Object>> joinInfo(@PathVariable Long envId) {
        return ApiResponse.success(swarmService.getJoinInfo(envId));
    }

    /**
     * 初始化 Swarm 集群（引导页一键创建）
     */
    @PostMapping("/init")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> init(@PathVariable Long envId,
                                  @RequestParam(required = false) String advertiseAddr) {
        swarmService.initSwarm(envId, advertiseAddr);
        return ApiResponse.success(null);
    }

    /**
     * 离开/解散 Swarm 集群
     */
    @PostMapping("/leave")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> leave(@PathVariable Long envId,
                                   @RequestParam(defaultValue = "false") boolean force) {
        swarmService.leaveSwarm(envId, force);
        return ApiResponse.success(null);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<Task>> tasks(@PathVariable Long envId) {
        return ApiResponse.success(swarmService.listTasks(envId));
    }

    /**
     * 探测副本终端的接入方式（本地 docker exec 还是经 SSH）
     */
    @GetMapping("/tasks/{taskId}/exec-target")
    public ApiResponse<Map<String, Object>> taskExecTarget(@PathVariable Long envId,
                                                           @PathVariable String taskId) {
        return ApiResponse.success(swarmService.resolveTaskExecTarget(envId, taskId));
    }

    /**
     * 单个副本（task）日志：对应 /tasks/{id}/logs，可跨节点取到该副本自己的日志
     */
    @GetMapping("/tasks/{taskId}/logs")
    public ApiResponse<String> taskLogs(@PathVariable Long envId,
                                        @PathVariable String taskId,
                                        @RequestParam(defaultValue = "100") int tail) {
        return ApiResponse.success(swarmService.getTaskLogs(envId, taskId, tail));
    }
}
