-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- P0-S3 성과 평가 (PerformanceReview) — 자기/매니저 평가 + KPI 자동 점수 + Self↔Manager 비교
-- p0_s3_contract.md §1/§2 SoT. audit 컬럼 DDL 패턴은 V20260611_002 정합
-- (id/tenant_id/created_at/updated_at/created_by/updated_by).

-- ─────────────────────────────────────────────────────────────────────
-- performance_review — cycle × employee 평가 단위 (10단계 상태기계)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE performance_review (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    cycle_id            UUID          NOT NULL,
    employee_id         UUID          NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    kpi_score           NUMERIC(5,2),
    mbo_score           NUMERIC(5,2),
    competency_score    NUMERIC(5,2),
    mra_score           NUMERIC(5,2),
    final_score         NUMERIC(5,2),
    final_grade         VARCHAR(10),
    self_comment        TEXT,
    manager_comment     TEXT,
    kpi_score_detail    JSONB,
    finalized_at        TIMESTAMPTZ,
    finalized_by        UUID,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_performance_review_status CHECK (status IN (
        'DRAFT','SELF_PENDING','SELF_SUBMITTED','MANAGER_PENDING','MANAGER_SUBMITTED',
        'CALIBRATION','FINALIZED','APPEAL_REQUESTED','APPEAL_RESOLVED','ARCHIVED')),
    CONSTRAINT uq_performance_review_cycle_employee UNIQUE (tenant_id, cycle_id, employee_id),
    CONSTRAINT fk_performance_review_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE
);
CREATE INDEX ix_performance_review_tenant_cycle_employee
    ON performance_review (tenant_id, cycle_id, employee_id);
CREATE INDEX ix_performance_review_tenant_status
    ON performance_review (tenant_id, status);
