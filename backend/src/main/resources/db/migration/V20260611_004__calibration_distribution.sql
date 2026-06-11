-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- P0-S4 캘리브레이션 + 분포 (CalibrationSession + RatingDistribution) — 강제 분포 시뮬레이터
-- p0_s4_contract.md §1/§2 SoT. audit 컬럼 DDL 패턴은 V20260611_003 정합
-- (id/tenant_id/created_at/updated_at/created_by/updated_by).
-- partial unique 2개 (§0-3) 는 talent 패턴 (CREATE UNIQUE INDEX ... WHERE org_unit_id IS NULL).

-- ─────────────────────────────────────────────────────────────────────
-- calibration_session — cycle 의 등급 보정 위원회 회의 단위 (5단계 상태기계).
-- cycle 당 다중 세션 허용 (UNIQUE 없음).
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE calibration_session (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    cycle_id            UUID          NOT NULL,
    owner_org_unit_id   UUID,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PLANNED',
    scheduled_at        TIMESTAMPTZ,
    participant_ids     JSONB,
    adjustment_log      JSONB,
    confirmed_at        TIMESTAMPTZ,
    confirmed_by        UUID,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_calibration_session_status CHECK (status IN (
        'PLANNED','IN_SESSION','ADJUSTED','CONFIRMED','CLOSED')),
    CONSTRAINT fk_calibration_session_cycle FOREIGN KEY (cycle_id)
        REFERENCES evaluation_cycle(id) ON DELETE CASCADE
);
CREATE INDEX ix_calibration_session_tenant_cycle_owner
    ON calibration_session (tenant_id, cycle_id, owner_org_unit_id);

-- ─────────────────────────────────────────────────────────────────────
-- rating_distribution — cycle (× orgUnit) 마지막 강제 배분 적용 결과 + 시뮬레이션 이력.
-- P0-S4 는 전사 (org_unit_id NULL) 행만 생성 (org 단위는 P0-S6 후).
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE rating_distribution (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    cycle_id            UUID          NOT NULL,
    org_unit_id         UUID,
    policy_distribution JSONB,
    actual_distribution JSONB,
    forced_applied      BOOLEAN       NOT NULL DEFAULT false,
    applied_at          TIMESTAMPTZ,
    applied_by          UUID,
    simulation_log      JSONB,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT fk_rating_distribution_cycle FOREIGN KEY (cycle_id)
        REFERENCES evaluation_cycle(id) ON DELETE CASCADE
);
-- §0-3 partial unique 2개 (talent 패턴) — 전사 1행 + org 단위 1행 분리.
CREATE UNIQUE INDEX uq_rating_distribution_cycle_companywide
    ON rating_distribution (tenant_id, cycle_id)
    WHERE org_unit_id IS NULL;
CREATE UNIQUE INDEX uq_rating_distribution_cycle_org
    ON rating_distribution (tenant_id, cycle_id, org_unit_id)
    WHERE org_unit_id IS NOT NULL;
