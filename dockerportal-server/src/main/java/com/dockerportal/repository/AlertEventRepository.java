package com.dockerportal.repository;

import com.dockerportal.model.entity.AlertEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {
    Page<AlertEvent> findByEnvironmentIdOrderByFiredAtDesc(Long environmentId, Pageable pageable);
    Page<AlertEvent> findAllByOrderByFiredAtDesc(Pageable pageable);
    Optional<AlertEvent> findByRuleIdAndStatus(Long ruleId, com.dockerportal.model.enums.AlertStatus status);
    List<AlertEvent> findByRuleIdOrderByFiredAtDesc(Long ruleId);
}
