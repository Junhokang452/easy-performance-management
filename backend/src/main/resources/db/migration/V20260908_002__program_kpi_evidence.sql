CREATE TABLE program_kpi_evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    program_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    goal_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    kpi_assignment_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    supersedes_evidence_id UUID,
    actual_cutoff_date DATE NOT NULL,
    preview_hash VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    applied_by_employee_id UUID,
    captured_at TIMESTAMPTZ NOT NULL,
    evidence_json JSONB NOT NULL,
    source_snapshot_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_program_kpi_evidence_goal_revision UNIQUE (tenant_id, goal_id, revision),
    CONSTRAINT uq_program_kpi_evidence_goal_hash UNIQUE (tenant_id, goal_id, preview_hash),
    CONSTRAINT uq_program_kpi_evidence_supersedes UNIQUE (supersedes_evidence_id),
    CONSTRAINT fk_program_kpi_evidence_program FOREIGN KEY (program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
    CONSTRAINT fk_program_kpi_evidence_participant FOREIGN KEY (participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
    CONSTRAINT fk_program_kpi_evidence_goal FOREIGN KEY (goal_id) REFERENCES program_goal(id) ON DELETE CASCADE,
    CONSTRAINT fk_program_kpi_evidence_supersedes FOREIGN KEY (supersedes_evidence_id) REFERENCES program_kpi_evidence(id),
    CONSTRAINT ck_program_kpi_evidence_revision CHECK (revision > 0),
    CONSTRAINT ck_program_kpi_evidence_hash CHECK (preview_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_program_kpi_evidence_tenant_goal
    ON program_kpi_evidence (tenant_id, goal_id, revision DESC);
CREATE INDEX ix_program_kpi_evidence_tenant_participant
    ON program_kpi_evidence (tenant_id, participant_id, goal_id);
