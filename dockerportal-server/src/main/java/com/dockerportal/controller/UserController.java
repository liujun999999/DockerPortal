package com.dockerportal.controller;

import com.dockerportal.audit.Auditable;
import com.dockerportal.model.enums.AuditLevel;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.dto.UserCreateRequest;
import com.dockerportal.model.dto.UserDTO;
import com.dockerportal.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserDTO>> list() {
        return ApiResponse.success(userService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }

    @PostMapping
    @Auditable(action = "USER_CREATE", resourceType = "user", level = AuditLevel.SECURITY, resourceId = "#result?.data?.username")
    public ApiResponse<UserDTO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    @DeleteMapping("/{id}")
    @Auditable(action = "USER_DELETE", resourceType = "user", level = AuditLevel.CRITICAL, resourceId = "#id")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/toggle-enabled")
    @Auditable(action = "USER_TOGGLE_ENABLED", resourceType = "user", level = AuditLevel.SECURITY, resourceId = "#id")
    public ApiResponse<UserDTO> toggleEnabled(@PathVariable Long id) {
        return ApiResponse.success(userService.toggleEnabled(id));
    }

    /** 管理员重置指定用户密码（重置后该用户下次登录强制改密） */
    @PostMapping("/{id}/reset-password")
    @Auditable(action = "USER_RESET_PASSWORD", resourceType = "user", level = AuditLevel.SECURITY, resourceId = "#id")
    public ApiResponse<UserDTO> resetPassword(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.success(userService.resetPassword(id, body.get("newPassword")));
    }
}
