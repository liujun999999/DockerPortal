package com.dockerportal.model.entity;

import com.dockerportal.model.enums.AlertSeverity;
import com.dockerportal.model.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "environment_id")
    private Long environmentId;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(nullable = false)
    private String metric;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(nullable = false)
    private Double threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertStatus status = AlertStatus.FIRING;

    @Column(name = "fired_at", nullable = false)
    private LocalDateTime firedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean notified = false;
}
