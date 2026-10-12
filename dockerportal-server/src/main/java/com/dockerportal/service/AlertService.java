package com.dockerportal.service;

import com.dockerportal.model.entity.AlertEvent;
import com.dockerportal.model.entity.AlertRule;
import com.dockerportal.model.enums.AlertStatus;
import com.dockerportal.repository.AlertEventRepository;
import com.dockerportal.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 告警服务 - 管理告警规则与告警事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    // ===== 告警规则管理 =====

    public List<AlertRule> listRules() {
        return alertRuleRepository.findAll();
    }

    public List<AlertRule> listRulesByEnv(Long envId) {
        return alertRuleRepository.findByEnvironmentId(envId);
    }

    public AlertRule getRule(Long id) {
        return alertRuleRepository.findById(id)
                .orElseThrow(() -> new com.dockerportal.exception.BusinessException(40400, "告警规则不存在"));
    }

    public AlertRule createRule(AlertRule rule) {
        return alertRuleRepository.save(rule);
    }

    public AlertRule updateRule(Long id, AlertRule updated) {
        AlertRule existing = getRule(id);
        existing.setName(updated.getName());
        existing.setEnvironmentId(updated.getEnvironmentId());
        existing.setTargetType(updated.getTargetType());
        existing.setTargetId(updated.getTargetId());
        existing.setMetric(updated.getMetric());
        existing.setOperator(updated.getOperator());
        existing.setThreshold(updated.getThreshold());
        existing.setDuration(updated.getDuration());
        existing.setSeverity(updated.getSeverity());
        existing.setEnabled(updated.getEnabled());
        existing.setWebhookUrl(updated.getWebhookUrl());
        return alertRuleRepository.save(existing);
    }

    public void deleteRule(Long id) {
        alertRuleRepository.deleteById(id);
        log.info("Alert rule deleted: {}", id);
    }

    public void toggleRule(Long id) {
        AlertRule rule = getRule(id);
        rule.setEnabled(!rule.getEnabled());
        alertRuleRepository.save(rule);
        log.info("Alert rule {} toggled to: {}", id, rule.getEnabled());
    }

    // ===== 告警事件管理 =====

    public Page<AlertEvent> listEvents(Long envId, int page, int size) {
        if (envId != null) {
            return alertEventRepository.findByEnvironmentIdOrderByFiredAtDesc(envId, PageRequest.of(page - 1, size));
        }
        return alertEventRepository.findAllByOrderByFiredAtDesc(PageRequest.of(page - 1, size));
    }

    public List<AlertEvent> listEventsByRule(Long ruleId) {
        return alertEventRepository.findByRuleIdOrderByFiredAtDesc(ruleId);
    }

    /**
     * 触发告警
     */
    public AlertEvent fireAlert(AlertRule rule, Long envId, Double currentValue) {
        // 检查是否已有未解决的告警
        Optional<AlertEvent> existing = alertEventRepository.findByRuleIdAndStatus(rule.getId(), AlertStatus.FIRING);
        if (existing.isPresent()) {
            return existing.get();
        }

        AlertEvent event = AlertEvent.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .environmentId(envId)
                .targetType(rule.getTargetType().name())
                .targetId(rule.getTargetId())
                .metric(rule.getMetric())
                .currentValue(currentValue)
                .threshold(rule.getThreshold())
                .severity(rule.getSeverity())
                .status(AlertStatus.FIRING)
                .firedAt(LocalDateTime.now())
                .notified(false)
                .build();

        AlertEvent saved = alertEventRepository.save(event);
        log.info("Alert fired: {} - {} = {}", rule.getName(), rule.getMetric(), currentValue);

        // 发送 Webhook 通知
        if (rule.getWebhookUrl() != null && !rule.getWebhookUrl().isEmpty()) {
            sendWebhook(rule.getWebhookUrl(), saved);
        }

        return saved;
    }

    /**
     * 解决告警
     */
    public void resolveAlert(Long ruleId) {
        Optional<AlertEvent> firing = alertEventRepository.findByRuleIdAndStatus(ruleId, AlertStatus.FIRING);
        firing.ifPresent(event -> {
            event.setStatus(AlertStatus.RESOLVED);
            event.setResolvedAt(LocalDateTime.now());
            alertEventRepository.save(event);
            log.info("Alert resolved: rule {}", ruleId);
        });
    }

    /**
     * 发送 Webhook 通知
     */
    private void sendWebhook(String webhookUrl, AlertEvent event) {
        try {
            // 使用 Java HttpClient 发送 Webhook
            var httpClient = java.net.http.HttpClient.newHttpClient();
            String json = String.format(
                    "{\"ruleName\":\"%s\",\"metric\":\"%s\",\"value\":%.2f,\"threshold\":%.2f,\"severity\":\"%s\",\"status\":\"%s\"}",
                    event.getRuleName(), event.getMetric(), event.getCurrentValue(),
                    event.getThreshold(), event.getSeverity(), event.getStatus()
            );
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            log.info("Webhook sent to: {}", webhookUrl);
        } catch (Exception e) {
            log.warn("Failed to send webhook to {}: {}", webhookUrl, e.getMessage());
        }
    }
}
