package com.dockerportal.model.dto;

import com.dockerportal.model.enums.ConnectionStatus;
import com.dockerportal.model.enums.EnvironmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvironmentDTO {
    private Long id;
    private String name;
    private EnvironmentType type;
    private String endpoint;
    private Boolean tlsEnabled;
    private ConnectionStatus status;
    private Boolean swarmMode;
    private String dockerVersion;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
