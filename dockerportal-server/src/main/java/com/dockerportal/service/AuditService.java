package com.dockerportal.service;

import com.dockerportal.model.entity.AuditLog;
import com.dockerportal.model.enums.AuditLevel;
import com.dockerportal.repository.AuditLogRepository;
import com.dockerportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    public void record(Long userId, String username, String action, String resourceType,
                       String resourceId, Long environmentId, String detail, String ip) {
        record(userId, username, action, resourceType, resourceId, environmentId,
                AuditLevel.OPERATION, detail, ip);
    }

    @Async
    public void record(Long userId, String username, String action, String resourceType,
                       String resourceId, Long environmentId, AuditLevel level, String detail, String ip) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .environmentId(environmentId)
                .level(level == null ? AuditLevel.OPERATION.name() : level.name())
                .result("SUCCESS")
                .detail(detail)
                .ip(ip)
                .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * 只有用户名的场景（如 WebSocket 终端会话）：自动补齐 userId
     */
    @Async
    public void recordByUsername(String username, String action, String resourceType,
                                 String resourceId, Long environmentId, String detail, String ip) {
        Long userId = userRepository.findByUsername(username)
                .map(u -> u.getId()).orElse(null);
        record(userId, username, action, resourceType, resourceId, environmentId, detail, ip);
    }

    /**
     * 供 {@link com.dockerportal.audit.AuditAspect} 调用：带分级、成功/失败、异常摘要与耗时。
     * username 可能为 null（未认证即失败的场景），落库统一兜底为 anonymous。
     */
    @Async
    public void recordFull(String username, String action, String resourceType, String resourceId,
                           Long environmentId, AuditLevel level, String detail, String ip,
                           boolean success, String errorMessage, Long durationMs) {
        String name = (username == null || username.isBlank()) ? "anonymous" : username;
        AuditLog auditLog = AuditLog.builder()
                .userId(userRepository.findByUsername(name).map(u -> u.getId()).orElse(null))
                .username(name)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .environmentId(environmentId)
                .level((level == null ? AuditLevel.OPERATION : level).name())
                .result(success ? "SUCCESS" : "FAILURE")
                .errorMessage(truncate(errorMessage, 1000))
                .durationMs(durationMs)
                .detail(truncate(detail, 2000))
                .ip(ip)
                .build();
        auditLogRepository.save(auditLog);
    }

    public Page<AuditLog> listAll(int page, int size) {
        return list(page, size, null, null, null);
    }

    /** 按分级 / 动作 / 用户名筛选（任一为 null 表示不过滤） */
    public Page<AuditLog> list(int page, int size, String level, String action, String username) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 200));
        String lv = blankToNull(level);
        String ac = blankToNull(action);
        String un = blankToNull(username);
        if (lv == null && ac == null && un == null) {
            return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return auditLogRepository.search(lv, ac, un, pageable);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
