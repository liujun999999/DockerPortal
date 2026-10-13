package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.SwarmService;
import com.github.dockerjava.api.model.SwarmNode;
import com.github.dockerjava.api.model.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/swarm/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final SwarmService swarmService;

    @GetMapping
    public ApiResponse<List<SwarmNode>> list(@PathVariable Long envId) {
        return ApiResponse.success(swarmService.listNodes(envId));
    }

    @GetMapping("/{nodeId}")
    public ApiResponse<SwarmNode> inspect(@PathVariable Long envId,
                                                     @PathVariable String nodeId) {
        return ApiResponse.success(swarmService.inspectNode(envId, nodeId));
    }

    /** 本节点上的任务列表，节点详情页用 */
    @GetMapping("/{nodeId}/tasks")
    public ApiResponse<List<Task>> tasks(@PathVariable Long envId,
                                         @PathVariable String nodeId) {
        return ApiResponse.success(swarmService.listNodeTasks(envId, nodeId));
    }

    /**
     * body 形如 { "labels": {"k":"v"}, "availability": "drain" }，两个字段都可省略。
     * labels 一旦传了就是整体替换，需提交完整集合，详见 service 层注释。
     */
    @PutMapping("/{nodeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "NODE_UPDATE", resourceType = "node", level = AuditLevel.CONFIG, resourceId = "#nodeId", environmentId = "#envId")
    public ApiResponse<Void> update(@PathVariable Long envId,
                                     @PathVariable String nodeId,
                                     @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) body.get("labels");
        String availability = (String) body.get("availability");
        swarmService.updateNode(envId, nodeId, labels, availability);
        return ApiResponse.success();
    }

    @DeleteMapping("/{nodeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "NODE_REMOVE", resourceType = "node", level = AuditLevel.CRITICAL, resourceId = "#nodeId", environmentId = "#envId")
    public ApiResponse<Void> remove(@PathVariable Long envId,
                                     @PathVariable String nodeId,
                                     @RequestParam(defaultValue = "false") boolean force) {
        swarmService.removeNode(envId, nodeId, force);
        return ApiResponse.success();
    }
}
