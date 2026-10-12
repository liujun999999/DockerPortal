package com.dockerportal.repository;

import com.dockerportal.model.entity.SshConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SshConfigRepository extends JpaRepository<SshConfig, Long> {
    List<SshConfig> findByEnvironmentId(Long environmentId);
}
