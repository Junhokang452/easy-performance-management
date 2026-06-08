-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- 자매품 9호 — 단계 2 (Model B per-tenant DB 진입) 가시화 박제 — G65 D=A, Task #100, 2026-06-08.
--
-- 본 마이그는 per-tenant DB Flyway fan-out 의 단계 2 진입 마커.
-- TenantBootstrap.bootstrap("PERFORMANCE", tenantId) 호출 시 lib NeonProvisioningService.activateSiblingDatabase
-- 가 본 위치(classpath:db/tenant)를 Flyway location 으로 사용해 per-tenant DB 에 순차 적용.
--
-- ADR-013 (Neon Model B) + ADR-024 (1 고객사 = 1 Neon 프로젝트 + per-tenant DB 분리) +
-- ADR-031 (자매품 9 × 3 토폴로지 — B2B-Enterprise per-tenant + SMB Shared) 정합:
--   * 본 자매품(performance) 의 per-tenant DB 는 `easyrecord-performance` (database_name = "performance")
--   * 단계 1 도메인 4개 (self_evaluation / personal_okr / reflection_journal / mentor_feedback) 는
--     V20260608_001 (db/migration) 이 단계 1 단일 DB 에 적용 → 단계 2 진입 후 per-tenant DB 분리 시
--     동일 스키마가 per-tenant DB 에 fan-out (Flyway 가 db/tenant location 으로 별도 시퀀스 관리)
--   * tenant_id 컬럼은 단계 1 그대로 보존 — 단계 2 진입 후에도 RLS 정책 (옵션 B 심층방어) 가능
--   * RlsTenantAspect set_config 호출은 per-tenant DB 에서 안전 no-op (lib RlsTenantAspect 가드)
--
-- 본 마이그 자체는 메타데이터 박제 (실제 도메인 스키마 변경 없음):
--   * db/migration V20260608_001 = 단계 1 도메인 4개 (단일 DB) — 보존
--   * 본 V20260608_001 (db/tenant) = 단계 2 진입 마커 + flyway_schema_history 검증용 + per-tenant DB 베이스라인 진입점
--
-- 토폴로지 정정 (2026-06-08, Task #98 ADR-031):
--   * ~~듀얼 모드 5호~~ 박제 폐기 → B2B-Enterprise per-tenant + SMB Shared (ware/hcm/recruit 패턴)
--   * 본 단계 2 = B2B-Enterprise per-tenant 본질 진입
--   * 단계 5 SMB Shared 옵션은 별도 슬라이스 (G67 결정 대기)
--
-- 후속:
--   * V20260608_002__stage3_jwt_5claim.sql (단계 3 BE-CC-2 JWT 5분리 진입 마커, 별도 슬라이스)
--   * V20260608_003__stage4_ec_fe.sql (단계 4 EC-FE 진입 마커, 별도 슬라이스)
--   * V20260608_004__stage5_smb_shared_entry.sql (단계 5 SMB Shared 진입 마커, G67 별도, 옵션)
--
-- 박제: backend/_workspace/PERFORMANCE_STAGE2_CUTOVER_2026-06-08.md

-- 단계 2 진입 가시화: 자매품 메타데이터 테이블 (idempotent CREATE)
CREATE TABLE IF NOT EXISTS performance_stage_marker (
    stage_code      varchar(32) PRIMARY KEY,
    entered_at      timestamptz NOT NULL DEFAULT now(),
    description     text NOT NULL,
    adr_refs        text NOT NULL,
    task_ref        varchar(32) NOT NULL
);

INSERT INTO performance_stage_marker (stage_code, description, adr_refs, task_ref)
VALUES (
    'STAGE_2_MODEL_B',
    '단계 2 — Model B per-tenant DB 진입 (NeonProvisioningService 통합 + lib BE 14 TenantBootstrap 3 SPI seam thin adapter / B2B-Enterprise per-tenant 본질, ADR-031 정합)',
    'ADR-013 (Neon Model B 통일) / ADR-024 (1 고객사 = 1 Neon 프로젝트 + 자매품 DB 분리) / ADR-031 (자매품 9 × 3 토폴로지 — B2B-Enterprise per-tenant + SMB Shared)',
    'Task #100 G65 D=A'
)
ON CONFLICT (stage_code) DO NOTHING;
