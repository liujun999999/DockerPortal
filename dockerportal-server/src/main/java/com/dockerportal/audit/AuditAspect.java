package com.dockerportal.audit;

import com.dockerportal.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 拦截 {@link Auditable} 方法，统一采集操作人 / IP / URI / 成功失败 / 异常摘要 / 耗时并落审计。
 *
 * 落库是同步的（项目未启用 @EnableAsync，AuditService 上的 @Async 实为空转，这里保持一致，
 * 不擅自改变全局线程语义）；整段包在 try/catch 里，审计失败绝不影响业务响应。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                emit(pjp, auditable, result, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("审计落库失败 action={}: {}", auditable.action(), e.getMessage());
            }
        }
    }

    private void emit(ProceedingJoinPoint pjp, Auditable auditable, Object result, Throwable error, long durMs) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Object[] args = pjp.getArgs();

        String username = currentUsername();
        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : clientIp(request);
        String uri = request == null ? method.getName() : request.getMethod() + " " + request.getRequestURI();

        String resourceId = eval(auditable.resourceId(), method, args, result, String.class);
        Long envId = eval(auditable.environmentId(), method, args, result, Long.class);

        boolean success = error == null;
        String err = success ? null : error.getClass().getSimpleName() + ": " + error.getMessage();

        auditService.recordFull(username, auditable.action(), auditable.resourceType(),
                resourceId, envId, auditable.level(), uri, ip, success, err, durMs);
    }

    /** SpEL 求值，作用域含方法入参（按参数名）与返回值 #result；表达式为空或求值异常返回 null */
    private <T> T eval(String expr, Method method, Object[] args, Object result, Class<T> type) {
        if (expr == null || expr.isBlank()) return null;
        try {
            MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);
            ctx.setVariable("result", result);
            return parser.parseExpression(expr).getValue(ctx, type);
        } catch (Exception e) {
            log.debug("审计 SpEL 求值失败 [{}]: {}", expr, e.getMessage());
            return null;
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
