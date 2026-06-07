/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.mentorfeedback.repository;

import com.easyperformance.domain.mentorfeedback.entity.MentorFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorFeedbackRepository extends JpaRepository<MentorFeedback, UUID> {

    Optional<MentorFeedback> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MentorFeedback> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<MentorFeedback> findAllByTenantIdAndMenteeId(UUID tenantId, UUID menteeId, Pageable pageable);

    Page<MentorFeedback> findAllByTenantIdAndMentorId(UUID tenantId, UUID mentorId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
