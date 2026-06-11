-- Copyright 2026 easy-performance-management contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- 인증 격상 — 사용자 계정 (bcrypt 인증, talent V20260611_002 동형 + performance 역할 5종).
-- role = SUPER_ADMIN / HR_ADMIN / DIRECTOR / MANAGER / EMPLOYEE.
-- employee_id = rm_employee 매핑 (P0-S6 hcm S2S 수신 후 채움, null 허용).
-- LIVE 영향: 0 — 신규 테이블 only (Render 미배포 repo).

CREATE TABLE user_account (
    id            UUID         NOT NULL PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    employee_id   UUID,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT uq_user_account_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT ck_user_account_role CHECK (role IN ('SUPER_ADMIN', 'HR_ADMIN', 'DIRECTOR', 'MANAGER', 'EMPLOYEE'))
);

CREATE INDEX ix_user_account_tenant_email ON user_account (tenant_id, email);
CREATE INDEX ix_user_account_tenant_role  ON user_account (tenant_id, role);

COMMENT ON TABLE user_account IS '사용자 계정 — 인증 격상 (bcrypt). role SUPER_ADMIN/HR_ADMIN/DIRECTOR/MANAGER/EMPLOYEE + rm_employee 매핑(선택)';
