package com.dockerportal.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stack 的 compose 原文。
 * Docker daemon 只在服务和网络上打 com.docker.stack.namespace 标签，并不保存 compose 文件本身，
 * 所以由本平台部署时必须把原文落库，"编辑 Stack" 才可能回填；
 * 用 docker CLI 或其他方式部署的 Stack 查不到记录，前端据此提示用户自行粘贴。
 */
@Entity
@Table(name = "stack_composes",
        uniqueConstraints = @UniqueConstraint(name = "uk_stack_compose", columnNames = {"environment_id", "stack_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StackCompose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    /** 与 Docker 侧的 com.docker.stack.namespace 取值一致 */
    @Column(name = "stack_name", nullable = false)
    private String stackName;

    /** YAML 原文，长度上限在 StackService 里校验 */
    @Column(nullable = false, length = 100000)
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
