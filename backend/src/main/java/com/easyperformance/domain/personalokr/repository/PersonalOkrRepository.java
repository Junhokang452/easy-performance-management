/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.personalokr.repository;

import com.easyperformance.domain.personalokr.entity.PersonalOkr;
import com.easyperformance.domain.personalokr.entity.PersonalOkrStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalOkrRepository extends JpaRepository<PersonalOkr, UUID> {

    Optional<PersonalOkr> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PersonalOkr> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<PersonalOkr> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId, Pageable pageable);

    Page<PersonalOkr> findAllByTenantIdAndStatus(UUID tenantId, PersonalOkrStatus status, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
