package com.dockerportal.model.dto;

import com.dockerportal.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
