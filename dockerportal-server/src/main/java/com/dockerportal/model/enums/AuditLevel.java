package com.dockerportal.model.enums;

/**
 * 审计事件分级。既作分类又作重要度，前端据此着色与筛选。
 * 一个动作只归一级，按“最需要关注的那一面”取：删除凭据既是凭据又是破坏，取 SECURITY。
 */
public enum AuditLevel {

    /** 认证、用户与权限、凭据（含密钥的 registry / ssh / environment）相关 */
    SECURITY,

    /** 不可逆破坏：各类删除、prune、swarm 离开、节点移除 */
    CRITICAL,

    /** 运行态变更：创建 / 启停 / 重启 / 扩缩容 / 部署 / 拉取推送 / 连接切换 */
    OPERATION,

    /** 配置编辑：标签、规格等不直接破坏数据的修改 */
    CONFIG
}
