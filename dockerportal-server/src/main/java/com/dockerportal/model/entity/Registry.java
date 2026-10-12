package com.dockerportal.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 镜像仓库凭据（Harbor / 阿里云 ACR / DockerHub 等）。
 * 全局配置；可选绑定 environmentId 限定只对某个 Docker 环境生效。
 */
@Entity
@Table(name = "registries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 仓库地址，如 harbor.example.com、registry.cn-hangzhou.aliyuncs.com；空表示 Docker Hub */
    @Column(nullable = false)
    private String url;

    /** 匿名仓库允许为空；保存时业务层把空白归一为空串 */
    @Column
    private String username;

    /** 匿名仓库（无需登录的公开镜像库）允许无密码 */
    @Column
    private String password;

    /** 命名空间/项目（可选），如 library、my-project，仅作提示用途 */
    @Column(name = "namespace_prefix")
    private String namespacePrefix;

    /** 生效环境（可选，为空表示所有环境可用） */
    @Column(name = "environment_id")
    private Long environmentId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 内置条目标识（例：Docker Hub 默认行），不落库，前端基于该标记隐藏删除/编辑 */
    @Transient
    private Boolean builtin;

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
