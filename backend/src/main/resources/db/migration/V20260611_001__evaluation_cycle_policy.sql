-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- P0-S1 EvaluationCycle + EvaluationPolicy (사용자 의사결정 G_PERF_E1~E10 흡수)
-- decisions_2026-06-11.md SoT.

CREATE TABLE evaluation_cycle (
    id              UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    name            VARCHAR(100) NOT NULL,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    cycle_type      VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_cycle_status CHECK (status IN ('PLANNED','ACTIVE','GOAL_SETTING','MID_REVIEW','SELF_REVIEW','MANAGER_REVIEW','CALIBRATION','FINALIZED','CANCELLED')),
    CONSTRAINT ck_cycle_type   CHECK (cycle_type IN ('HALF_ANNUAL','ANNUAL','QUARTERLY','MONTHLY','CUSTOM')),
    CONSTRAINT ck_cycle_period CHECK (period_end >= period_start),
    CONSTRAINT uq_cycle_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX ix_cycle_tenant_status ON evaluation_cycle (tenant_id, status);
CREATE INDEX ix_cycle_tenant_period ON evaluation_cycle (tenant_id, period_start, period_end);

CREATE TABLE evaluation_policy (
    id                              UUID         NOT NULL PRIMARY KEY,
    tenant_id                       UUID         NOT NULL,
    cycle_id                        UUID         NOT NULL,
    distribution_mode               VARCHAR(20)  NOT NULL,
    rating_scale                    VARCHAR(20)  NOT NULL,
    appeal_enabled                  BOOLEAN      NOT NULL DEFAULT false,
    bsc_enabled                     BOOLEAN      NOT NULL DEFAULT false,
    achievement_log_cutoff_days     INTEGER      NOT NULL DEFAULT 3,
    forced_distribution             JSONB,
    created_at                      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by                      UUID,
    updated_by                      UUID,
    CONSTRAINT ck_policy_distribution_mode CHECK (distribution_mode IN ('HYBRID','FORCED','ABSOLUTE')),
    CONSTRAINT ck_policy_rating_scale      CHECK (rating_scale IN ('S_A_B_C_D','ONE_TO_FIVE','ONE_TO_HUNDRED')),
    CONSTRAINT ck_policy_cutoff_days       CHECK (achievement_log_cutoff_days >= 0 AND achievement_log_cutoff_days <= 30),
    CONSTRAINT uq_policy_tenant_cycle      UNIQUE (tenant_id, cycle_id),
    CONSTRAINT fk_policy_cycle             FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE
);
CREATE INDEX ix_policy_tenant_cycle ON evaluation_policy (tenant_id, cycle_id);
