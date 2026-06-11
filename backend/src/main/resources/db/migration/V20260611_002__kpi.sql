-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- P0-S2 KPI 도메인 (KpiTree / KpiNode / KpiAssignment / KpiActual)
-- p0_s2_contract.md §1/§2 SoT. audit 컬럼 DDL 패턴은 V20260611_001 정합
-- (id/tenant_id/created_at/updated_at/created_by/updated_by).

-- ─────────────────────────────────────────────────────────────────────
-- kpi_tree — cycle 안의 조직 레벨별 KPI 컨테이너
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE kpi_tree (
    id                  UUID         NOT NULL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    cycle_id            UUID         NOT NULL,
    owner_org_unit_id   UUID,
    name                VARCHAR(100) NOT NULL,
    level               VARCHAR(20)  NOT NULL,
    bsc_enabled         BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_kpi_tree_level CHECK (level IN ('CORPORATE','DIVISION','TEAM','INDIVIDUAL')),
    CONSTRAINT fk_kpi_tree_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE
);
CREATE INDEX ix_kpi_tree_tenant_cycle_owner ON kpi_tree (tenant_id, cycle_id, owner_org_unit_id);

-- ─────────────────────────────────────────────────────────────────────
-- kpi_node — 트리 안의 단일 KPI (self-referencing parent)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE kpi_node (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    tree_id             UUID          NOT NULL,
    parent_id           UUID,
    label               VARCHAR(200)  NOT NULL,
    weight              NUMERIC(5,4)  NOT NULL,
    target              NUMERIC(18,4),
    unit                VARCHAR(20),
    bsc_perspective     VARCHAR(20),
    source              VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    source_config       JSONB,
    cascade_from_id     UUID,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_kpi_node_weight          CHECK (weight > 0 AND weight <= 1),
    CONSTRAINT ck_kpi_node_bsc_perspective CHECK (bsc_perspective IS NULL OR bsc_perspective IN ('FINANCIAL','CUSTOMER','INTERNAL_PROCESS','LEARNING_GROWTH')),
    CONSTRAINT ck_kpi_node_source          CHECK (source IN ('MANUAL','HCM','EXTERNAL')),
    CONSTRAINT fk_kpi_node_tree            FOREIGN KEY (tree_id) REFERENCES kpi_tree(id) ON DELETE CASCADE,
    CONSTRAINT fk_kpi_node_parent          FOREIGN KEY (parent_id) REFERENCES kpi_node(id)
);
CREATE INDEX ix_kpi_node_tenant_tree_parent ON kpi_node (tenant_id, tree_id, parent_id);

-- ─────────────────────────────────────────────────────────────────────
-- kpi_assignment — node × employee 배정 (개인 override)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE kpi_assignment (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    kpi_node_id         UUID          NOT NULL,
    employee_id         UUID          NOT NULL,
    weight              NUMERIC(5,4),
    target_override     NUMERIC(18,4),
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_kpi_assignment_weight       CHECK (weight IS NULL OR (weight > 0 AND weight <= 1)),
    CONSTRAINT uq_kpi_assignment_tenant_node_employee UNIQUE (tenant_id, kpi_node_id, employee_id),
    CONSTRAINT fk_kpi_assignment_node         FOREIGN KEY (kpi_node_id) REFERENCES kpi_node(id) ON DELETE CASCADE
);
CREATE INDEX ix_kpi_assignment_tenant_employee ON kpi_assignment (tenant_id, employee_id);

-- ─────────────────────────────────────────────────────────────────────
-- kpi_actual — append-only 실적 (supersedes 체인)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE kpi_actual (
    id                  UUID          NOT NULL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    kpi_assignment_id   UUID          NOT NULL,
    as_of_date          DATE          NOT NULL,
    actual_value        NUMERIC(18,4) NOT NULL,
    source              VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    reported_by         UUID,
    evidence_url        VARCHAR(500),
    comment             TEXT,
    supersedes_id       UUID,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_kpi_actual_source     CHECK (source IN ('MANUAL','AUTO','IMPORT')),
    CONSTRAINT uq_kpi_actual_supersedes UNIQUE (supersedes_id),
    CONSTRAINT fk_kpi_actual_assignment FOREIGN KEY (kpi_assignment_id) REFERENCES kpi_assignment(id) ON DELETE CASCADE,
    CONSTRAINT fk_kpi_actual_supersedes FOREIGN KEY (supersedes_id) REFERENCES kpi_actual(id)
);
CREATE INDEX ix_kpi_actual_tenant_assignment_asof ON kpi_actual (tenant_id, kpi_assignment_id, as_of_date DESC);
