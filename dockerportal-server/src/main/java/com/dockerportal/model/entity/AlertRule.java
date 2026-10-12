package com.dockerportal.model.entity;

import com.dockerportal.model.enums.AlertSeverity;
import com.dockerportal.model.enums.ComparisonOperator;
import com.dockerportal.model.enums.TargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "environment_id")
    private Long environmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(nullable = false)
    private String metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComparisonOperator operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    @Builder.Default
    private Integer duration = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.WARNING;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "webhook_url")
    private String webhookUrl;

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
