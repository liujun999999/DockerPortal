package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.VolumeService;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/volumes")
@RequiredArgsConstructor
public class VolumeController {

    private final VolumeService volumeService;

    @GetMapping
    public ApiResponse<ListVolumesResponse> list(@PathVariable Long envId) {
        return ApiResponse.success(volumeService.listVolumes(envId));
    }

    @GetMapping("/{volumeName}")
    public ApiResponse<InspectVolumeResponse> inspect(@PathVariable Long envId,
                                                      @PathVariable String volumeName) {
        return ApiResponse.success(volumeService.inspectVolume(envId, volumeName));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<String> create(@PathVariable Long envId, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String driver = (String) body.get("driver");
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) body.get("labels");
        return ApiResponse.success(volumeService.createVolume(envId, name, driver, labels));
    }

    @DeleteMapping("/{volumeName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> remove(@PathVariable Long envId,
                                    @PathVariable String volumeName,
                                    @RequestParam(defaultValue = "false") boolean force) {
        volumeService.removeVolume(envId, volumeName, force);
        return ApiResponse.success();
    }
}
