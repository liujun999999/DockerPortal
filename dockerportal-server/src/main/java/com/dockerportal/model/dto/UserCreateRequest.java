package com.dockerportal.model.dto;

import com.dockerportal.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String email;
    private UserRole role = UserRole.VIEWER;
}
