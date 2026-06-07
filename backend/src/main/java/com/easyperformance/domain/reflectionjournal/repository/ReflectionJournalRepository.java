/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.reflectionjournal.repository;

import com.easyperformance.domain.reflectionjournal.entity.ReflectionJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReflectionJournalRepository extends JpaRepository<ReflectionJournal, UUID> {

    Optional<ReflectionJournal> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ReflectionJournal> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<ReflectionJournal> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
