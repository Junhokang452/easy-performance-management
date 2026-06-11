-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- P0-S5 성과 리포트 (PerformanceReport) — append-only HR 일괄 발행 + 사원 결과 조회.
-- p0_s5_contract.md §1/§2 SoT. audit 컬럼 DDL 패턴은 V20260611_004 정합
-- (id/tenant_id/created_at/updated_at/created_by/updated_by).
-- content jsonb = 발행 시점 §5 동결 스냅샷 (이후 불변). UPDATE 는 service 가 viewed_at/acknowledged/
-- acknowledged_at 3 컬럼만 dirty-write. supersedes_id UNIQUE = supersede 체인 선형성 (KpiActual 패턴).

-- ─────────────────────────────────────────────────────────────────────
-- performance_report — cycle × review × employee 발행 리포트 (append-only).
-- 정정은 supersede 신규 row (supersedes_id 가 원본). active = supersede 안 된 행.
-- DELETE 경로 없음 — cycle/review ON DELETE CASCADE 만.
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE performance_report (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    cycle_id            UUID          NOT NULL,
    review_id           UUID          NOT NULL,
    employee_id         UUID          NOT NULL,
    published_at        TIMESTAMPTZ   NOT NULL,
    published_by        UUID,
    content             JSONB         NOT NULL,
    viewed_at           TIMESTAMPTZ,
    acknowledged        BOOLEAN       NOT NULL DEFAULT false,
    acknowledged_at     TIMESTAMPTZ,
    supersedes_id       UUID,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT fk_performance_report_cycle FOREIGN KEY (cycle_id)
        REFERENCES evaluation_cycle(id) ON DELETE CASCADE,
    CONSTRAINT fk_performance_report_review FOREIGN KEY (review_id)
        REFERENCES performance_review(id) ON DELETE CASCADE,
    CONSTRAINT fk_performance_report_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES performance_report(id),
    -- 체인 선형성 — 한 원본 row 는 최대 1번만 supersede (KpiActual uq 패턴).
    CONSTRAINT uq_performance_report_supersedes UNIQUE (supersedes_id)
);
CREATE INDEX ix_performance_report_tenant_cycle_employee
    ON performance_report (tenant_id, cycle_id, employee_id);
CREATE INDEX ix_performance_report_tenant_review
    ON performance_report (tenant_id, review_id);
