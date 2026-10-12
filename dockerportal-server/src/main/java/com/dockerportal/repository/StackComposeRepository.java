package com.dockerportal.repository;

import com.dockerportal.model.entity.StackCompose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StackComposeRepository extends JpaRepository<StackCompose, Long> {

    Optional<StackCompose> findByEnvironmentIdAndStackName(Long environmentId, String stackName);

    /** 列表页一次性取出该环境所有已记录的 compose，避免逐行查库 */
    List<StackCompose> findByEnvironmentId(Long environmentId);
}
