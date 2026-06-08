-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- 자매품 9호 — 단계 5 SMB Shared 옵션 진입 (G67 D=A, Task #105, 2026-06-08).
-- ADR-031 정합 (ware/hcm/recruit SMB 진입 가이드 패턴 정합).
--
-- 토폴로지:
--   * SMB Shared = 단일 Neon 프로젝트 `shared-smb-easy-performance-management` + 단일 DB `easyshare_performance_management`
--   * Model A + RLS tenant_id 격리 (논리 격리, 다수 tenant 공유)
--   * B2B-Enterprise per-tenant DB (본질, application-prod.yml) 와 별도 옵션
--
-- 본 마이그 = SMB Shared 진입 시 4 도메인 테이블에 RLS 정책 강제 적용:
--   1. self_evaluation
--   2. personal_okr
--   3. reflection_journal
--   4. mentor_feedback
--
-- RLS 컨텍스트 주입 (Spring AOP — lib BE 18 RlsTenantAspect):
--   set_config('app.tenant_id', '<uuid>', true)
--   @Transactional 진입 시 TX-bound 커넥션에 주입 (PgBouncer transaction mode 정합).
--
-- 적용 위치:
--   * application-smb.yml: flyway.locations=classpath:db/migration,classpath:db/smb
--   * application-prod.yml / application-dev.yml: 본 위치 제외 (B2B-Enterprise per-tenant DB 본질 보존)
--
-- 박제:
--   * backend/_workspace/PERFORMANCE_STAGE5_SMB_OPTION_2026-06-08.md
--   * _workspace/PERFORMANCE_SMB_ENTRY_GUIDE_2026-06-08.md
--
-- 정합 참고:
--   * easy-ware/_workspace/WARE_SMB_ENTRY_GUIDE_2026-06-07.md §3.2 RLS 정책 SQL 표준
--   * easy-standards/_workspace/ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md

-- ─────────────────────────────────────────────────────────────────────────
-- 1. self_evaluation — RLS 활성화 + tenant 격리 정책
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE self_evaluation ENABLE ROW LEVEL SECURITY;
ALTER TABLE self_evaluation FORCE ROW LEVEL SECURITY;  -- 관리자도 RLS 적용

CREATE POLICY self_evaluation_tenant_isolation
    ON self_evaluation
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON POLICY self_evaluation_tenant_isolation ON self_evaluation
    IS 'SMB Shared 토폴로지 — tenant_id 격리 정책 (lib BE 18 RlsTenantAspect set_config 정합, ADR-031)';

-- ─────────────────────────────────────────────────────────────────────────
-- 2. personal_okr — RLS 활성화 + tenant 격리 정책
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE personal_okr ENABLE ROW LEVEL SECURITY;
ALTER TABLE personal_okr FORCE ROW LEVEL SECURITY;

CREATE POLICY personal_okr_tenant_isolation
    ON personal_okr
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON POLICY personal_okr_tenant_isolation ON personal_okr
    IS 'SMB Shared 토폴로지 — tenant_id 격리 정책 (lib BE 18 RlsTenantAspect set_config 정합, ADR-031)';

-- ─────────────────────────────────────────────────────────────────────────
-- 3. reflection_journal — RLS 활성화 + tenant 격리 정책
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE reflection_journal ENABLE ROW LEVEL SECURITY;
ALTER TABLE reflection_journal FORCE ROW LEVEL SECURITY;

CREATE POLICY reflection_journal_tenant_isolation
    ON reflection_journal
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON POLICY reflection_journal_tenant_isolation ON reflection_journal
    IS 'SMB Shared 토폴로지 — tenant_id 격리 정책 (lib BE 18 RlsTenantAspect set_config 정합, ADR-031)';

-- ─────────────────────────────────────────────────────────────────────────
-- 4. mentor_feedback — RLS 활성화 + tenant 격리 정책
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE mentor_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE mentor_feedback FORCE ROW LEVEL SECURITY;

CREATE POLICY mentor_feedback_tenant_isolation
    ON mentor_feedback
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON POLICY mentor_feedback_tenant_isolation ON mentor_feedback
    IS 'SMB Shared 토폴로지 — tenant_id 격리 정책 (lib BE 18 RlsTenantAspect set_config 정합, ADR-031)';

-- ─────────────────────────────────────────────────────────────────────────
-- 5. SMB Shared 진입 메타데이터 가시화
-- ─────────────────────────────────────────────────────────────────────────
-- performance_stage_marker 테이블은 단계 2 마이그에서 이미 생성됨 (db/tenant/V20260608_001).
-- SMB Shared 환경에서는 단계 2 마이그가 미적용 → idempotent 생성으로 안전.
CREATE TABLE IF NOT EXISTS performance_stage_marker (
    stage_code      varchar(32) PRIMARY KEY,
    entered_at      timestamptz NOT NULL DEFAULT now(),
    description     text NOT NULL,
    adr_refs        text NOT NULL,
    task_ref        varchar(32) NOT NULL
);

INSERT INTO performance_stage_marker (stage_code, description, adr_refs, task_ref)
VALUES (
    'STAGE_5_SMB_SHARED',
    '단계 5 — SMB Shared 옵션 진입 (단일 DB shared-smb-easy-performance-management + Model A + RLS tenant_id 격리, ware/hcm/recruit 패턴 정합, B2B-Enterprise per-tenant 본질 보존)',
    'ADR-022 (자매품 정식 편입) / ADR-031 (자매품 9 × 3 토폴로지 — B2B-Enterprise per-tenant + SMB Shared) / ADR-013 (Neon Model B 본질 보존)',
    'Task #105 G67 D=A'
)
ON CONFLICT (stage_code) DO NOTHING;
