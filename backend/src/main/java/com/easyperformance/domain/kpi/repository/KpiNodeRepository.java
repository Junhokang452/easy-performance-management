/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.repository;

import com.easyperformance.domain.kpi.entity.KpiNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KpiNode Repository — ADR-026 명명 정합. 트리 내 노드 조회 + 형제 가중치 합산 + 자식 존재 검사.
 *
 * <p>easy-ware 규칙 #10 — 모든 변형에 tenant_id 명시. JPQL 은 안전 패턴 (positional 회피, named param).
 */
@Repository
public interface KpiNodeRepository extends JpaRepository<KpiNode, UUID> {

    Optional<KpiNode> findByIdAndTenantId(UUID id, UUID tenantId);

    /** 트리 전체 노드 (flat) — 생성순. */
    List<KpiNode> findAllByTenantIdAndTreeIdOrderByCreatedAtAsc(UUID tenantId, UUID treeId);

    /**
     * 같은 부모(형제) 노드 — 가중치 합 검증용. parentId null (루트 형제) 도 지원하기 위해
     * IS NULL 분기를 JPQL 로 명시 (Spring Data 파생 쿼리는 null 파라미터를 = 비교로 처리해 미스매치).
     */
    @Query("""
        SELECT n FROM KpiNode n
        WHERE n.tenantId = :tenantId
          AND n.treeId = :treeId
          AND ((:parentId IS NULL AND n.parentId IS NULL)
               OR (:parentId IS NOT NULL AND n.parentId = :parentId))
        """)
    List<KpiNode> findSiblings(@Param("tenantId") UUID tenantId,
                               @Param("treeId") UUID treeId,
                               @Param("parentId") UUID parentId);

    /** 자식 노드 존재 여부 — delete 가드. */
    boolean existsByTenantIdAndParentId(UUID tenantId, UUID parentId);
}
