-- Secure evaluation workspace: roster, reviewer ownership, goal agreement,
-- intermediate performance review, and formal result feedback.

CREATE TABLE evaluation_participant (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    exclusion_reason TEXT,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_eval_participant_status CHECK (status IN ('ACTIVE','EXCLUDED')),
    CONSTRAINT uq_eval_participant_cycle_employee UNIQUE (tenant_id, cycle_id, employee_id),
    CONSTRAINT fk_eval_participant_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE
);
CREATE INDEX ix_eval_participant_tenant_cycle_status ON evaluation_participant (tenant_id, cycle_id, status);
CREATE INDEX ix_eval_participant_tenant_employee ON evaluation_participant (tenant_id, employee_id);

CREATE TABLE evaluation_reviewer_assignment (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    reviewer_employee_id UUID NOT NULL,
    reviewer_type VARCHAR(20) NOT NULL,
    review_round INTEGER NOT NULL DEFAULT 1,
    weight NUMERIC(5,4) NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_eval_reviewer_type CHECK (reviewer_type IN ('MANAGER','PEER','HR')),
    CONSTRAINT ck_eval_reviewer_status CHECK (status IN ('ASSIGNED','IN_PROGRESS','SUBMITTED','REVOKED')),
    CONSTRAINT ck_eval_reviewer_round CHECK (review_round > 0),
    CONSTRAINT ck_eval_reviewer_weight CHECK (weight > 0 AND weight <= 1),
    CONSTRAINT uq_eval_reviewer_assignment UNIQUE
        (tenant_id, participant_id, reviewer_employee_id, reviewer_type, review_round),
    CONSTRAINT fk_eval_reviewer_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_reviewer_participant FOREIGN KEY (participant_id) REFERENCES evaluation_participant(id) ON DELETE CASCADE
);
CREATE INDEX ix_eval_reviewer_tenant_cycle ON evaluation_reviewer_assignment (tenant_id, cycle_id);
CREATE INDEX ix_eval_reviewer_tenant_reviewer_status
    ON evaluation_reviewer_assignment (tenant_id, reviewer_employee_id, status);

CREATE TABLE evaluation_goal (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    kpi_assignment_id UUID NOT NULL UNIQUE,
    kpi_node_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    decision_comment TEXT,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_eval_goal_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED')),
    CONSTRAINT fk_eval_goal_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_goal_participant FOREIGN KEY (participant_id) REFERENCES evaluation_participant(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_goal_kpi_node FOREIGN KEY (kpi_node_id) REFERENCES kpi_node(id) ON DELETE RESTRICT,
    CONSTRAINT fk_eval_goal_kpi_assignment FOREIGN KEY (kpi_assignment_id) REFERENCES kpi_assignment(id) ON DELETE RESTRICT
);
CREATE INDEX ix_eval_goal_tenant_cycle_employee ON evaluation_goal (tenant_id, cycle_id, employee_id);
CREATE INDEX ix_eval_goal_tenant_participant_status ON evaluation_goal (tenant_id, participant_id, status);

CREATE TABLE intermediate_performance_review (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    progress_summary TEXT NOT NULL,
    achievements TEXT,
    blockers TEXT,
    support_needed TEXT,
    manager_comment TEXT,
    employee_submitted_at TIMESTAMPTZ,
    manager_completed_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_intermediate_review_status CHECK (status IN ('DRAFT','EMPLOYEE_SUBMITTED','MANAGER_COMPLETED')),
    CONSTRAINT uq_intermediate_review_participant UNIQUE (tenant_id, participant_id),
    CONSTRAINT fk_intermediate_review_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycle(id) ON DELETE CASCADE,
    CONSTRAINT fk_intermediate_review_participant FOREIGN KEY (participant_id) REFERENCES evaluation_participant(id) ON DELETE CASCADE
);
CREATE INDEX ix_intermediate_review_tenant_cycle_status
    ON intermediate_performance_review (tenant_id, cycle_id, status);

CREATE TABLE performance_feedback (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    report_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    comment TEXT,
    completed_at TIMESTAMPTZ,
    completed_by UUID,
    appeal_reason TEXT,
    appealed_at TIMESTAMPTZ,
    resolution VARCHAR(30),
    resolution_comment TEXT,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_performance_feedback_status CHECK (status IN ('DRAFT','COMPLETED','ACCEPTED','APPEALED','RESOLVED')),
    CONSTRAINT ck_performance_feedback_resolution CHECK (resolution IS NULL OR resolution IN ('UPHELD','ADJUSTMENT_REQUIRED')),
    CONSTRAINT uq_performance_feedback_report UNIQUE (tenant_id, report_id),
    CONSTRAINT fk_performance_feedback_report FOREIGN KEY (report_id) REFERENCES performance_report(id) ON DELETE CASCADE,
    CONSTRAINT fk_performance_feedback_participant FOREIGN KEY (participant_id) REFERENCES evaluation_participant(id) ON DELETE CASCADE
);
CREATE INDEX ix_performance_feedback_tenant_participant_status
    ON performance_feedback (tenant_id, participant_id, status);
