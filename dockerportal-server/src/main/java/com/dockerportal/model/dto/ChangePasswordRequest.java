package com.dockerportal.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自助修改密码请求
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
