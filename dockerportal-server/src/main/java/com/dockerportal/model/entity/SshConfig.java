package com.dockerportal.model.entity;

import com.dockerportal.model.enums.SshAuthType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ssh_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SshConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(name = "node_id")
    private String nodeId;

    /** 主机显示名称（自定义主机使用，可选） */
    @Column(name = "name")
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    @Builder.Default
    private Integer port = 22;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    private SshAuthType authType;

    @Column(columnDefinition = "TEXT")
    private String password;

    @Column(name = "private_key", columnDefinition = "TEXT")
    private String privateKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
