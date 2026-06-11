-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- P0-S6 hcm S2S 수신 (core-master read-model) — rm_employee / rm_org_unit / rm_assignment.
-- p0_s6_contract.md §3 SoT. talent V20260610_002 rm_* DDL 사본 (store-hr `d95bf62` 원형).
-- id = 소스 SoR 식별자 (외부 주입, UuidV7 생성 없음). source_version BIGINT 단조 멱등 기준.
-- 쓰기 진입점은 ReadModelSyncService 단일 — 그 외 read-only 소비 (성과평가 employeeId 표시·선택).
-- audit 4컬럼 (created_at/updated_at/created_by/updated_by) = lib TenantAwareAuditEntity 정합
-- (V20260611_005 패턴). tenant_id 선두 복합 인덱스 (easy-ware 규칙 #2).

-- ─────────────────────────────────────────────────────────────────────
-- rm_employee — 사원 Read Model (easy-hcm SoR 소비).
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE rm_employee (
    id              UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    employee_no     VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    org_unit_id     UUID,
    employment_type VARCHAR(20),
    source_version  BIGINT       NOT NULL,
    synced_at       TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX ix_rm_employee_tenant_no  ON rm_employee (tenant_id, employee_no);
CREATE INDEX ix_rm_employee_tenant_org ON rm_employee (tenant_id, org_unit_id);

-- ─────────────────────────────────────────────────────────────────────
-- rm_org_unit — 조직 Read Model (easy-hcm SoR 소비).
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE rm_org_unit (
    id             UUID         NOT NULL PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    code           VARCHAR(50)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    parent_id      UUID,
    org_type       VARCHAR(20),
    source_version BIGINT       NOT NULL,
    synced_at      TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX ix_rm_org_unit_tenant_code ON rm_org_unit (tenant_id, code);

-- ─────────────────────────────────────────────────────────────────────
-- rm_assignment — 발령 Read Model (easy-hcm SoR 소비).
-- job_code = easy-job-management FK-by-code (ADR-019 승계).
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE rm_assignment (
    id             UUID        NOT NULL PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    employee_id    UUID        NOT NULL,
    org_unit_id    UUID,
    position_code  VARCHAR(50),
    grade_code     VARCHAR(50),
    job_code       VARCHAR(50),
    effective_from DATE,
    effective_to   DATE,
    source_version BIGINT      NOT NULL,
    synced_at      TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX ix_rm_assignment_tenant_employee ON rm_assignment (tenant_id, employee_id);
