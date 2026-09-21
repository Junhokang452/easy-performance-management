-- S1 direct-manager reviewer-line automation.
-- Additive HCM capability fields remain nullable for legacy wire compatibility.

ALTER TABLE rm_assignment
    ADD COLUMN manager_employee_id UUID,
    ADD COLUMN deleted BOOLEAN,
    ADD COLUMN source_system VARCHAR(20) NOT NULL DEFAULT 'LEGACY';

CREATE INDEX ix_rm_assignment_tenant_manager
    ON rm_assignment (tenant_id, manager_employee_id);

CREATE TABLE program_reviewer_line_run (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    program_id UUID NOT NULL,
    preview_hash VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_reviewer_line_run_hash UNIQUE (tenant_id, program_id, preview_hash),
    CONSTRAINT fk_reviewer_line_run_program FOREIGN KEY (program_id)
        REFERENCES evaluation_program(id) ON DELETE CASCADE
);

CREATE INDEX ix_reviewer_line_run_tenant_program
    ON program_reviewer_line_run (tenant_id, program_id, created_at);

ALTER TABLE program_reviewer_assignment
    ADD COLUMN assignment_origin VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_assignment_id UUID,
    ADD COLUMN source_version BIGINT,
    ADD COLUMN source_as_of_date DATE,
    ADD COLUMN automation_run_id UUID,
    ADD CONSTRAINT ck_program_reviewer_origin
        CHECK (assignment_origin IN ('MANUAL', 'XLSX', 'HCM_MANAGER')),
    ADD CONSTRAINT fk_program_reviewer_automation_run
        FOREIGN KEY (automation_run_id) REFERENCES program_reviewer_line_run(id) ON DELETE RESTRICT;

CREATE INDEX ix_program_reviewer_tenant_origin
    ON program_reviewer_assignment (tenant_id, assignment_origin, automation_run_id);
