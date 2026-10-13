package com.dockerportal.audit;

import com.dockerportal.model.enums.AuditLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 打在写操作 Controller 方法上，由 {@link AuditAspect} 统一落审计，免去逐处手写 auditService。
 *
 * resourceId / environmentId 支持 SpEL，作用域是方法入参（按参数名，如 {@code #containerId}、
 * {@code #req.name}）与返回值 {@code #result}；求值失败会留空而不影响主流程。
 * 操作人、IP、URI、成功/失败、异常摘要、耗时由切面自动采集，无需在此声明。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** 动作码，如 CONTAINER_STOP、SERVICE_DELETE */
    String action();

    /** 资源类型，如 container / service / stack / image / volume / network / node / user / environment / registry / secret / config */
    String resourceType();

    /** 分级，默认按普通运行态变更 */
    AuditLevel level() default AuditLevel.OPERATION;

    /** 资源标识的 SpEL，例：#containerId、#serviceId、#req.name；留空则不记具体 id */
    String resourceId() default "";

    /** 环境 id 的 SpEL，例：#envId；留空则不记 */
    String environmentId() default "";
}
