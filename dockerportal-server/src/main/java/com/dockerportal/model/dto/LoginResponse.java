package com.dockerportal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String username;
    private String role;
    /** 首登/被重置后需强制修改密码，前端据此拦截到改密页 */
    private Boolean mustChangePassword;
}
