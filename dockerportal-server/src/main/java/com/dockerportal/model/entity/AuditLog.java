package com.dockerportal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "environment_id")
    private Long environmentId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 50)
    private String ip;

    /** 分级：SECURITY / CRITICAL / OPERATION / CONFIG，见 AuditLevel */
    @Column(length = 20)
    private String level;

    /** 结果：SUCCESS / FAILURE */
    @Column(length = 10)
    private String result;

    /** 失败时的异常摘要，成功为 null */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 服务端处理耗时（毫秒） */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
