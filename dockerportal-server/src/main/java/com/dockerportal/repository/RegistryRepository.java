package com.dockerportal.repository;

import com.dockerportal.model.entity.Registry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistryRepository extends JpaRepository<Registry, Long> {

    List<Registry> findByEnvironmentIdIsNullOrderByCreatedAtDesc();

    List<Registry> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);
}
