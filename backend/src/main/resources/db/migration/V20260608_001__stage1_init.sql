-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- 자매품 9호 (easy-performance-management) 단계 1 BE-CC-1 초기 스키마.
-- 도메인 4개: self_evaluation + personal_okr + reflection_journal + mentor_feedback.
--
-- 단계 1 = 단일 DB + tenant_id 컬럼 보유 (Model A 흉내). 단계 2 Model B per-tenant DB 진입 시
-- tenant_id 컬럼 보존 + RLS 정책 SQL 박제.
--
-- 인덱스: tenant_id 선두 복합 인덱스 필수 (easy-ware 12 규칙 #2).
-- 공통 컬럼: created_at / updated_at / created_by / updated_by (lib BaseAuditEntity).

-- ─────────────────────────────────────────────────────────────────────────
-- 1. self_evaluation (자기평가)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE self_evaluation (
    id              UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    employee_id     UUID         NOT NULL,
    cycle_id        UUID,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    content         TEXT,
    score           DOUBLE PRECISION,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_self_evaluation_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVIEWED', 'FINALIZED')),
    CONSTRAINT ck_self_evaluation_period CHECK (period_end >= period_start)
);

CREATE INDEX ix_self_evaluation_tenant_employee ON self_evaluation (tenant_id, employee_id);
CREATE INDEX ix_self_evaluation_tenant_cycle    ON self_evaluation (tenant_id, cycle_id);
CREATE INDEX ix_self_evaluation_tenant_status   ON self_evaluation (tenant_id, status);

-- ─────────────────────────────────────────────────────────────────────────
-- 2. personal_okr (개인 OKR)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE personal_okr (
    id              UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    employee_id     UUID         NOT NULL,
    objective       VARCHAR(500) NOT NULL,
    progress        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_personal_okr_status   CHECK (status IN ('ACTIVE', 'AT_RISK', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_personal_okr_progress CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT ck_personal_okr_period   CHECK (period_end >= period_start)
);

CREATE INDEX ix_personal_okr_tenant_employee ON personal_okr (tenant_id, employee_id);
CREATE INDEX ix_personal_okr_tenant_status   ON personal_okr (tenant_id, status);
CREATE INDEX ix_personal_okr_tenant_period   ON personal_okr (tenant_id, period_end);

-- ─────────────────────────────────────────────────────────────────────────
-- 3. reflection_journal (회고 저널)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE reflection_journal (
    id               UUID         NOT NULL PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    employee_id      UUID         NOT NULL,
    reflection_date  DATE         NOT NULL,
    method           VARCHAR(20)  NOT NULL DEFAULT 'KPT',
    content          TEXT         NOT NULL,
    is_private       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ck_reflection_journal_method CHECK (method IN ('KPT', 'FOUR_LS', 'SSC'))
);

CREATE INDEX ix_reflection_journal_tenant_employee ON reflection_journal (tenant_id, employee_id);
CREATE INDEX ix_reflection_journal_tenant_date     ON reflection_journal (tenant_id, reflection_date);

-- ─────────────────────────────────────────────────────────────────────────
-- 4. mentor_feedback (멘토 피드백)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE mentor_feedback (
    id              UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    mentor_id       UUID         NOT NULL,
    mentee_id       UUID         NOT NULL,
    feedback_date   DATE         NOT NULL,
    category        VARCHAR(20)  NOT NULL DEFAULT 'GROWTH',
    content         TEXT         NOT NULL,
    acknowledged    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_mentor_feedback_category CHECK (category IN ('GROWTH', 'RECOGNITION', 'COACHING', 'CONVERSATION'))
);

CREATE INDEX ix_mentor_feedback_tenant_mentee ON mentor_feedback (tenant_id, mentee_id);
CREATE INDEX ix_mentor_feedback_tenant_mentor ON mentor_feedback (tenant_id, mentor_id);
CREATE INDEX ix_mentor_feedback_tenant_date   ON mentor_feedback (tenant_id, feedback_date);

COMMENT ON TABLE self_evaluation    IS '자기평가 (단계 1 BE-CC-1, 자매품 9호)';
COMMENT ON TABLE personal_okr       IS '개인 OKR (단계 1 BE-CC-1, 자매품 9호)';
COMMENT ON TABLE reflection_journal IS '회고 저널 (단계 1 BE-CC-1, 자매품 9호)';
COMMENT ON TABLE mentor_feedback    IS '멘토 피드백 (단계 1 BE-CC-1, 자매품 9호)';
