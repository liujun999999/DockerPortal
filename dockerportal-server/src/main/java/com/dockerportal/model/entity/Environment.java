package com.dockerportal.model.entity;

import com.dockerportal.model.enums.ConnectionStatus;
import com.dockerportal.model.enums.EnvironmentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "environments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentType type;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "tls_enabled")
    @Builder.Default
    private Boolean tlsEnabled = false;

    @Column(name = "tls_ca_cert", columnDefinition = "TEXT")
    private String tlsCaCert;

    @Column(name = "tls_cert", columnDefinition = "TEXT")
    private String tlsCert;

    @Column(name = "tls_key", columnDefinition = "TEXT")
    private String tlsKey;

    @Column(name = "ssh_user")
    private String sshUser;

    @Column(name = "ssh_key", columnDefinition = "TEXT")
    private String sshKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    @Column(name = "swarm_mode")
    @Builder.Default
    private Boolean swarmMode = false;

    @Column(name = "docker_version")
    private String dockerVersion;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
