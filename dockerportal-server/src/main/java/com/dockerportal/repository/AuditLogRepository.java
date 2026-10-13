package com.dockerportal.repository;

import com.dockerportal.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 任一条件为 null 即不参与过滤；用 (:x IS NULL OR ...) 拼可选条件 */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:level IS NULL OR a.level = :level)
              AND (:action IS NULL OR a.action = :action)
              AND (:username IS NULL OR a.username = :username)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("level") String level,
                          @Param("action") String action,
                          @Param("username") String username,
                          Pageable pageable);
}
