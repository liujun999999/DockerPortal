package com.dockerportal.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 首次安装：创建管理员账号的请求
 */
@Data
public class SetupRequest {

    /** 留空则由服务端取默认值 admin */
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
