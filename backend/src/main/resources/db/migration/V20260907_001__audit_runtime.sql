-- Product fan-out of platform audit SoT; required by the existing entity scan.
-- Existing migration checksums are preserved.
-- BE-CC-1 AuditEvent 테이블 (lib BE 12번째 단위, Task #605, 2026-06-06).
--
-- 추출 모범: mra `audit_events` (actor_user_id/event_type/outcome) 를 일반화 — actor_type/action/
-- resource_* + 체인 무결성(prev_hash → hash) 박제. mra cutover (W2) 시 본 스키마로 재구성.
--
-- 듀얼 모드 분기:
--   * B2B (ADR-024): per-tenant DB 행. tenant_id = 고객사 UUID, user_id NULL 가능
--   * B2C (ADR-029/030): control DB 공통 테넌트. tenant_id NULL, user_id = 개인 UUID + RLS 격리
--
-- 자매품 fan-out: 본 파일 내용을 자매품 영역 prefix 로 복사 (예: easy-mra V_92_005__audit_event.sql).
-- mra 기존 audit_events 가 있으면 별도 migration 슬라이스에서 DROP + 본 스키마로 재구성 (회귀 분리).
--
-- 게이트: easyplatform.audit.enabled (기본 false). 본 테이블은 게이트 OFF 여도 생성 — recorder 가 항상
-- INSERT 가능해야 한다. autoconfig 만 OFF.
--
-- 참조: easy-standards 09-database §4 · BE-CC-1 audit · ADR-030 듀얼 모드.

CREATE TABLE IF NOT EXISTS audit_event (
    id                  uuid PRIMARY KEY,

    actor_type          varchar(50) NOT NULL,
    actor_id            uuid,
    tenant_id           uuid,
    user_id             uuid,

    action              varchar(100) NOT NULL,
    resource_type       varchar(100),
    resource_id         uuid,

    ip                  varchar(45),
    user_agent          text,
    metadata            jsonb,

    prev_hash           char(64),
    hash                char(64) NOT NULL,

    timestamp           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT audit_event_actor_type_check
        CHECK (actor_type IN ('USER', 'SYSTEM', 'EXTERNAL'))
);

-- 4 종 조회 인덱스 — entity @Index 와 1:1 정렬. timestamp DESC 조회 패턴.
CREATE INDEX IF NOT EXISTS idx_audit_event_actor
    ON audit_event(actor_id, timestamp DESC)
    WHERE actor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_event_tenant
    ON audit_event(tenant_id, timestamp DESC)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_event_user
    ON audit_event(user_id, timestamp DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_event_action
    ON audit_event(action, timestamp DESC);

COMMENT ON TABLE audit_event IS
    'BE-CC-1 AuditEvent. 체인 무결성 prev_hash → hash 박제. lib easy-platform-core 12번째 단위.';
COMMENT ON COLUMN audit_event.actor_type IS
    'USER(인증 사용자) / SYSTEM(스케줄러·배치) / EXTERNAL(외부 시스템·웹훅).';
COMMENT ON COLUMN audit_event.prev_hash IS
    '직전 행의 hash. 위변조 시 직후 행 hash 검증 실패 — 행 단위 결연 무결성.';
COMMENT ON COLUMN audit_event.hash IS
    'SHA-256(canonical fields + prev_hash) 64자 hex. recorder 가 박제.';

-- BE-CC-1 audit_event hash 컬럼 타입 정합 (2026-06-11, easy-job-management 합병 fresh-boot 스모크 실측).
--
-- 발견: V_AUDIT_001 의 prev_hash/hash 가 char(64) 로 박제되어 있으나 lib AuditEvent 엔티티는
-- @Column(length=64) varchar(64) 매핑 — Hibernate ddl-auto=validate 환경(자매품 per-tenant/단일 DB 사본)에서
-- "wrong column type encountered ... found [bpchar], but expecting [varchar(64)]" 부팅 실패를 유발하는
-- lib 자체 DDL↔엔티티 불일치 잠복 결함 (easy-standards 90-conformance/jobmanagement-merge-2026-06-10.md §7.1#4).
--
-- 정합 전략: V_AUDIT_001 파일은 이미 적용된 control DB 의 Flyway checksum 보존을 위해 무수정(hcm V1002 교훈),
-- 본 V_AUDIT_002 증분으로 타입만 전환. hash 값은 항상 SHA-256 64자 hex(패딩 없음)라 USING rtrim 은 보수적 안전망.
-- 컬럼 부재/이미 varchar 인 환경에서도 멱등 (DO 블록 가드).
--
-- 자매품 복사 가이드: 자매품 자체 audit_event 사본이 char(64) 인 경우 자기 버전 영역으로 본 파일을 복사
-- (easy-job-management 는 V20260611_001/_025 에서 이미 varchar 로 생성 — 복사 불요).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'audit_event'
          AND column_name = 'hash' AND data_type = 'character'
    ) THEN
        ALTER TABLE audit_event ALTER COLUMN hash TYPE varchar(64) USING rtrim(hash);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'audit_event'
          AND column_name = 'prev_hash' AND data_type = 'character'
    ) THEN
        ALTER TABLE audit_event ALTER COLUMN prev_hash TYPE varchar(64) USING rtrim(prev_hash);
    END IF;
END $$;
