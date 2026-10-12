package com.dockerportal.model.dto;

import com.dockerportal.model.enums.EnvironmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnvironmentCreateRequest {

    @NotBlank(message = "环境名称不能为空")
    private String name;

    @NotNull(message = "连接类型不能为空")
    private EnvironmentType type;

    @NotBlank(message = "连接地址不能为空")
    private String endpoint;

    private Boolean tlsEnabled = false;
    private String tlsCaCert;
    private String tlsCert;
    private String tlsKey;
    private String sshUser;
    private String sshKey;
}
