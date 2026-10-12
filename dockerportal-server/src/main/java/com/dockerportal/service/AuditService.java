package com.dockerportal.service;

import com.dockerportal.model.entity.AuditLog;
import com.dockerportal.repository.AuditLogRepository;
import com.dockerportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .environmentId(environmentId)
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

    public Page<AuditLog> listAll(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, size));
    }
}
