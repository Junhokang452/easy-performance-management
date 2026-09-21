-- Configurable enterprise evaluation programs. Definitions and results are versioned;
-- operational rows use optimistic locks, while audit/revision/calculation rows are append-only.

CREATE TABLE evaluation_program (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(120) NOT NULL,
 evaluation_year INTEGER NOT NULL, as_of_date DATE NOT NULL, starts_on DATE NOT NULL, ends_on DATE NOT NULL,
 kind VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 definition_revision INTEGER NOT NULL DEFAULT 1, definition_json JSONB NOT NULL,
 applied_item_revision INTEGER NOT NULL DEFAULT 0, opened_at TIMESTAMP, finalized_at TIMESTAMP,
 row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_tenant_year_name UNIQUE(tenant_id,evaluation_year,name),
 CONSTRAINT ck_program_period CHECK(ends_on>=starts_on),
 CONSTRAINT ck_program_kind CHECK(kind IN('PERFORMANCE','COMPETENCY','COMBINED','MULTI_RATER')),
 CONSTRAINT ck_program_status CHECK(status IN('DRAFT','OPEN','FINALIZED','CANCELLED'))
);
CREATE INDEX ix_program_tenant_status_year ON evaluation_program(tenant_id,status,evaluation_year);
CREATE INDEX ix_program_tenant_period ON evaluation_program(tenant_id,starts_on,ends_on);

CREATE TABLE evaluation_program_revision (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, revision INTEGER NOT NULL,
 reason VARCHAR(500) NOT NULL, definition_json JSONB NOT NULL, actor_employee_id UUID,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_revision UNIQUE(tenant_id,program_id,revision),
 CONSTRAINT fk_program_revision_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE
);
CREATE INDEX ix_program_revision_tenant_program ON evaluation_program_revision(tenant_id,program_id,revision);

CREATE TABLE program_participant (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, employee_id UUID NOT NULL,
 assignment_id UUID, assignment_key VARCHAR(80) NOT NULL, org_unit_id UUID, group_id UUID, group_name VARCHAR(100),
 attributes_json JSONB NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 weight_percent NUMERIC(7,4) NOT NULL DEFAULT 100, current_stage VARCHAR(30),
 stage_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', current_round INTEGER NOT NULL DEFAULT 0,
 result_published BOOLEAN NOT NULL DEFAULT FALSE, exclusion_reason VARCHAR(500), row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_participant_assignment UNIQUE(tenant_id,program_id,employee_id,assignment_key),
 CONSTRAINT fk_program_participant_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_participant_status CHECK(status IN('ACTIVE','EXCLUDED','DELETED')),
 CONSTRAINT ck_program_participant_stage_status CHECK(stage_status IN('NOT_STARTED','READY','IN_PROGRESS','COMPLETED','SKIPPED','BLOCKED')),
 CONSTRAINT ck_program_participant_weight CHECK(weight_percent>0 AND weight_percent<=100),
 CONSTRAINT ck_program_participant_round CHECK(current_round>=0 AND current_round<=3)
);
CREATE INDEX ix_program_participant_tenant_program_status ON program_participant(tenant_id,program_id,status);
CREATE INDEX ix_program_participant_tenant_employee ON program_participant(tenant_id,employee_id);

CREATE TABLE program_reviewer_assignment (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 reviewer_employee_id UUID NOT NULL, reviewer_role VARCHAR(30) NOT NULL, review_round INTEGER NOT NULL,
 weight_percent NUMERIC(7,4) NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED', row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_reviewer UNIQUE(tenant_id,participant_id,reviewer_employee_id,reviewer_role,review_round),
 CONSTRAINT fk_program_reviewer_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_reviewer_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_reviewer_role CHECK(reviewer_role IN('SELF','AGREEMENT_REVIEWER','CHECKER','REVIEWER','ADJUSTER','FINAL_FEEDBACK')),
 CONSTRAINT ck_program_reviewer_status CHECK(status IN('ASSIGNED','IN_PROGRESS','COMPLETED','REVOKED')),
 CONSTRAINT ck_program_reviewer_round CHECK(review_round>=0 AND review_round<=3),
 CONSTRAINT ck_program_reviewer_weight CHECK(weight_percent>=0 AND weight_percent<=100)
);
CREATE INDEX ix_program_reviewer_tenant_participant ON program_reviewer_assignment(tenant_id,participant_id);
CREATE INDEX ix_program_reviewer_tenant_employee ON program_reviewer_assignment(tenant_id,reviewer_employee_id,status);

CREATE TABLE program_goal (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 catalog_item_id UUID, department_goal_id UUID, title VARCHAR(200) NOT NULL, definition TEXT NOT NULL,
 weight_percent NUMERIC(7,4) NOT NULL, target_value NUMERIC(18,4), unit VARCHAR(20),
 achievement_levels JSONB NOT NULL DEFAULT '[]'::jsonb, evidence_json JSONB NOT NULL DEFAULT '[]'::jsonb,
 status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', draft_opinion TEXT, decision_opinion TEXT,
 achieved_level_code VARCHAR(30), achievement_summary TEXT, revision INTEGER NOT NULL DEFAULT 1,
 row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT fk_program_goal_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_goal_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_goal_weight CHECK(weight_percent>0 AND weight_percent<=100),
 CONSTRAINT ck_program_goal_status CHECK(status IN('DRAFT','AGREEMENT_REQUESTED','AGREED','RETURNED','SELF_REPORTED'))
);
CREATE INDEX ix_program_goal_tenant_participant ON program_goal(tenant_id,participant_id,status);

CREATE TABLE program_goal_event (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, goal_id UUID NOT NULL, revision INTEGER NOT NULL,
 status VARCHAR(30) NOT NULL, operation VARCHAR(40) NOT NULL, opinion TEXT, actor_employee_id UUID,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT fk_program_goal_event_goal FOREIGN KEY(goal_id) REFERENCES program_goal(id) ON DELETE CASCADE
);
CREATE INDEX ix_program_goal_event_tenant_goal ON program_goal_event(tenant_id,goal_id,revision);

CREATE TABLE program_intermediate_review (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 checker_employee_id UUID NOT NULL, opinion TEXT NOT NULL, task_evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', completed_at TIMESTAMP, row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_intermediate_participant UNIQUE(tenant_id,participant_id),
 CONSTRAINT fk_program_intermediate_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_intermediate_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_intermediate_status CHECK(status IN('DRAFT','COMPLETED','INVALIDATED'))
);

CREATE TABLE program_review_submission (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 reviewer_assignment_id UUID, reviewer_assignment_key VARCHAR(80) NOT NULL, actor_employee_id UUID NOT NULL,
 role VARCHAR(30) NOT NULL, review_round INTEGER NOT NULL, answers_json JSONB NOT NULL DEFAULT '[]'::jsonb,
 overall_opinion TEXT, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', completed_at TIMESTAMP,
 row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_submission_assignment UNIQUE(tenant_id,participant_id,reviewer_assignment_key,review_round),
 CONSTRAINT fk_program_submission_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_submission_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_submission_reviewer FOREIGN KEY(reviewer_assignment_id) REFERENCES program_reviewer_assignment(id) ON DELETE RESTRICT,
 CONSTRAINT ck_program_submission_role CHECK(role IN('SELF','AGREEMENT_REVIEWER','CHECKER','REVIEWER','ADJUSTER','FINAL_FEEDBACK')),
 CONSTRAINT ck_program_submission_status CHECK(status IN('DRAFT','COMPLETED','INVALIDATED')),
 CONSTRAINT ck_program_submission_round CHECK(review_round>=0 AND review_round<=3)
);
CREATE INDEX ix_program_submission_tenant_participant ON program_review_submission(tenant_id,participant_id,status);

CREATE TABLE program_calculation (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 revision INTEGER NOT NULL, contributions_json JSONB NOT NULL, raw_score NUMERIC(9,4) NOT NULL,
 normalized_score NUMERIC(9,4) NOT NULL, adjusted_score NUMERIC(9,4) NOT NULL,
 calculated_grade VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, formula VARCHAR(1000) NOT NULL,
 warnings_json JSONB NOT NULL DEFAULT '[]'::jsonb, calculated_at TIMESTAMP NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_calculation_revision UNIQUE(tenant_id,participant_id,revision),
 CONSTRAINT fk_program_calculation_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_calculation_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_calculation_status CHECK(status IN('DRAFT','FINAL'))
);
CREATE INDEX ix_program_calculation_tenant_program ON program_calculation(tenant_id,program_id,status);

CREATE TABLE program_adjustment (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 calculation_id UUID NOT NULL, before_score NUMERIC(9,4) NOT NULL, before_grade VARCHAR(20) NOT NULL,
 adjusted_score NUMERIC(9,4) NOT NULL, adjusted_grade VARCHAR(20) NOT NULL, reason TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', actor_employee_id UUID NOT NULL, completed_at TIMESTAMP,
 row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_adjustment_participant UNIQUE(tenant_id,participant_id),
 CONSTRAINT fk_program_adjustment_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_adjustment_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_adjustment_calculation FOREIGN KEY(calculation_id) REFERENCES program_calculation(id) ON DELETE RESTRICT,
 CONSTRAINT ck_program_adjustment_status CHECK(status IN('DRAFT','COMPLETED'))
);

CREATE TABLE program_feedback (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID NOT NULL,
 writer_employee_id UUID NOT NULL, comment TEXT, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 appeal_reason TEXT, resolution VARCHAR(20), resolution_comment TEXT, delivered_at TIMESTAMP, resolved_at TIMESTAMP,
 row_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT uq_program_feedback_participant UNIQUE(tenant_id,participant_id),
 CONSTRAINT fk_program_feedback_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_feedback_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_feedback_status CHECK(status IN('DRAFT','DELIVERED','AGREED','APPEALED','RESOLVED')),
 CONSTRAINT ck_program_feedback_resolution CHECK(resolution IS NULL OR resolution IN('UPHELD','SCORE_ADJUSTED'))
);
CREATE INDEX ix_program_feedback_tenant_status ON program_feedback(tenant_id,program_id,status);

CREATE TABLE program_notification (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, recipient_employee_id UUID NOT NULL,
 channel VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL, subject VARCHAR(200) NOT NULL, body TEXT NOT NULL,
 sent_at TIMESTAMP, read_at TIMESTAMP, failure_reason VARCHAR(1000),
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT fk_program_notification_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_notification_channel CHECK(channel IN('IN_APP','EMAIL')),
 CONSTRAINT ck_program_notification_status CHECK(status IN('READY','CONFIG_REQUIRED','SENT','FAILED','READ'))
);
CREATE INDEX ix_program_notification_tenant_recipient ON program_notification(tenant_id,recipient_employee_id,created_at);
CREATE INDEX ix_program_notification_tenant_status ON program_notification(tenant_id,status);

CREATE TABLE program_audit_event (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, participant_id UUID,
 event_type VARCHAR(40) NOT NULL, reason VARCHAR(1000) NOT NULL, actor_employee_id UUID, details_json JSONB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT fk_program_audit_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT fk_program_audit_participant FOREIGN KEY(participant_id) REFERENCES program_participant(id) ON DELETE SET NULL
);
CREATE INDEX ix_program_audit_tenant_program ON program_audit_event(tenant_id,program_id,created_at);
CREATE INDEX ix_program_audit_tenant_participant ON program_audit_event(tenant_id,participant_id,created_at);

CREATE TABLE program_guide_attachment (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, program_id UUID NOT NULL, filename VARCHAR(255) NOT NULL,
 content_type VARCHAR(120) NOT NULL, size BIGINT NOT NULL, uploaded_by UUID, content BYTEA NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
 CONSTRAINT fk_program_guide_program FOREIGN KEY(program_id) REFERENCES evaluation_program(id) ON DELETE CASCADE,
 CONSTRAINT ck_program_guide_size CHECK(size>0 AND size<=10485760)
);
CREATE INDEX ix_program_guide_tenant_program ON program_guide_attachment(tenant_id,program_id);
