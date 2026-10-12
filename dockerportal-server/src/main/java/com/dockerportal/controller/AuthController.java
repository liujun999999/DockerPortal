package com.dockerportal.controller;

import com.dockerportal.model.dto.ApiResponse;
import com.dockerportal.model.dto.ChangePasswordRequest;
import com.dockerportal.model.dto.LoginRequest;
import com.dockerportal.model.dto.LoginResponse;
import com.dockerportal.model.dto.UserDTO;
import com.dockerportal.security.JwtTokenProvider;
import com.dockerportal.service.AuthService;
import com.dockerportal.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /** 当前登录用户信息（供前端刷新后恢复 mustChangePassword 等拦截状态） */
    @GetMapping("/me")
    public ApiResponse<?> me() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ApiResponse.success(userService.findByUsername(username));
    }

    /** 自助修改密码（任何已登录用户，包括待改密用户）；成功后重签发新 token（清除 must_change claim），前端需替换本地 token */
    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changeOwnPassword(username, request.getOldPassword(), request.getNewPassword());
        UserDTO me = userService.findByUsername(username);
        String newToken = jwtTokenProvider.generateAccessToken(username, me.getRole().name(), false);
        return ApiResponse.success(Map.of("accessToken", newToken));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestParam String username,
                                      @RequestParam String password,
                                      @RequestParam(required = false) String email) {
        authService.register(username, password, email);
        return ApiResponse.success();
    }
}
