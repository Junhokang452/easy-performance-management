/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.entity;

/**
 * 성과 평가 상태기계 10단계 — P0-S3 (p0_s3_contract.md §1/§3).
 *
 * <p>도메인 본질: 한 직원의 한 사이클 평가가 거치는 lifecycle — HR/매니저 생성(DRAFT) → 자기평가 단계
 * (SELF_PENDING → SELF_SUBMITTED) → 매니저 평가 단계(MANAGER_PENDING → MANAGER_SUBMITTED) →
 * 캘리브레이션(CALIBRATION) → 확정(FINALIZED) → (이의제기 P0-S10) → (아카이브 P2-S3).
 *
 * <p><strong>P0-S3 허용 전이는 §3 매트릭스 4개 + submit 전용 2개만</strong>:
 * <pre>
 *   transition 으로 허용:
 *     DRAFT            → SELF_PENDING       (cycle=SELF_REVIEW)
 *     SELF_SUBMITTED   → MANAGER_PENDING    (cycle=MANAGER_REVIEW)
 *     MANAGER_SUBMITTED→ CALIBRATION        (cycle=CALIBRATION)
 *     CALIBRATION      → FINALIZED          (cycle=CALIBRATION, finalScore/finalGrade 산출)
 *   전용 endpoint (transition 으로 불가):
 *     SELF_PENDING     → SELF_SUBMITTED     (submit-self, cycle=SELF_REVIEW)
 *     MANAGER_PENDING  → MANAGER_SUBMITTED  (submit-manager, cycle=MANAGER_REVIEW + 스냅샷·kpiScore 산출)
 * </pre>
 *
 * <p>{@link #APPEAL_REQUESTED} / {@link #APPEAL_RESOLVED} (P0-S10) + {@link #ARCHIVED} (P2-S3) 는
 * <b>enum 박제만</b> — P0-S3 에서는 어떤 전이로도 진입 불가.
 *
 * <p>DB CHECK 제약: {@code ck_review_status}.
 */
public enum ReviewStatus {
    DRAFT,
    SELF_PENDING,
    SELF_SUBMITTED,
    MANAGER_PENDING,
    MANAGER_SUBMITTED,
    CALIBRATION,
    FINALIZED,
    APPEAL_REQUESTED,
    APPEAL_RESOLVED,
    ARCHIVED
}
