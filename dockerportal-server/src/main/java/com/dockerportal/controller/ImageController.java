package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.service.ImageService;
import com.github.dockerjava.api.model.Image;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/env/{envId}/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @GetMapping
    public ApiResponse<List<Image>> list(@PathVariable Long envId) {
        return ApiResponse.success(imageService.listImages(envId));
    }

    @GetMapping("/{imageId}")
    public ApiResponse<?> inspect(@PathVariable Long envId, @PathVariable String imageId) {
        return ApiResponse.success(imageService.inspectImage(envId, imageId));
    }

    @PostMapping("/pull")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "IMAGE_PULL", resourceType = "image", level = AuditLevel.OPERATION, environmentId = "#envId")
    public ApiResponse<Void> pull(@PathVariable Long envId, @RequestBody Map<String, Object> body) {
        String imageName = (String) body.get("imageName");
        String tag = (String) body.getOrDefault("tag", "latest");
        Long registryId = null;
        Object rid = body.get("registryId");
        if (rid instanceof Number n) registryId = n.longValue();
        else if (rid instanceof String s && !s.isBlank()) registryId = Long.parseLong(s);
        imageService.pullImage(envId, imageName, tag, registryId);
        return ApiResponse.success();
    }

    /** 不提供 force 参数：使用中的镜像（含被已停止容器、Swarm 服务引用）一律拒删，由 service 层拦截 */
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "IMAGE_REMOVE", resourceType = "image", level = AuditLevel.CRITICAL, resourceId = "#imageId", environmentId = "#envId")
    public ApiResponse<Void> remove(@PathVariable Long envId, @PathVariable String imageId) {
        imageService.removeImage(envId, imageId);
        return ApiResponse.success();
    }

    @PostMapping("/{imageId}/tag")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "IMAGE_TAG", resourceType = "image", level = AuditLevel.OPERATION, resourceId = "#imageId", environmentId = "#envId")
    public ApiResponse<Void> tag(@PathVariable Long envId,
                                 @PathVariable String imageId,
                                 @RequestParam String repo,
                                 @RequestParam String tag) {
        imageService.tagImage(envId, imageId, repo, tag);
        return ApiResponse.success();
    }

    /** 推送镜像：自动先打目标 tag 再 push；registryId 缺省时按目标仓库 host 自动匹配已保存凭据 */
    @PostMapping("/{imageId}/push")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Auditable(action = "IMAGE_PUSH", resourceType = "image", level = AuditLevel.OPERATION, resourceId = "#imageId", environmentId = "#envId")
    public ApiResponse<Void> push(@PathVariable Long envId,
                                  @PathVariable String imageId,
                                  @RequestParam String repo,
                                  @RequestParam String tag,
                                  @RequestParam(required = false) Long registryId) {
        imageService.pushImage(envId, imageId, repo, tag, registryId);
        return ApiResponse.success();
    }

    @GetMapping("/{imageId}/history")
    public ApiResponse<?> history(@PathVariable Long envId, @PathVariable String imageId) {
        return ApiResponse.success(imageService.getImageHistory(envId, imageId));
    }
}
