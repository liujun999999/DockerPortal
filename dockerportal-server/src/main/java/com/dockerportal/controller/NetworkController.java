package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.NetworkService;
import com.github.dockerjava.api.model.Network;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/networks")
@RequiredArgsConstructor
public class NetworkController {

    private final NetworkService networkService;

    @GetMapping
    public ApiResponse<List<Network>> list(@PathVariable Long envId) {
        return ApiResponse.success(networkService.listNetworks(envId));
    }

    @GetMapping("/{networkId}")
    public ApiResponse<Network> inspect(@PathVariable Long envId, @PathVariable String networkId) {
        return ApiResponse.success(networkService.inspectNetwork(envId, networkId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<String> create(@PathVariable Long envId, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String driver = (String) body.getOrDefault("driver", "bridge");
        boolean internal = Boolean.TRUE.equals(body.get("internal"));
        boolean attachable = Boolean.TRUE.equals(body.get("attachable"));
        return ApiResponse.success(networkService.createNetwork(envId, name, driver, internal, attachable));
    }

    @DeleteMapping("/{networkId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> remove(@PathVariable Long envId, @PathVariable String networkId) {
        networkService.removeNetwork(envId, networkId);
        return ApiResponse.success();
    }

    @PostMapping("/{networkId}/connect")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> connect(@PathVariable Long envId,
                                     @PathVariable String networkId,
                                     @RequestParam String containerId) {
        networkService.connectContainer(envId, networkId, containerId);
        return ApiResponse.success();
    }

    @PostMapping("/{networkId}/disconnect")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> disconnect(@PathVariable Long envId,
                                        @PathVariable String networkId,
                                        @RequestParam String containerId) {
        networkService.disconnectContainer(envId, networkId, containerId);
        return ApiResponse.success();
    }
}
