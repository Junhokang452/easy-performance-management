CREATE TABLE program_reminder_run (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    program_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    preview_hash VARCHAR(64) NOT NULL,
    reminder_on DATE NOT NULL,
    policy_version VARCHAR(60) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_employee_id UUID,
    response_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_program_reminder_run_idempotency UNIQUE (tenant_id, program_id, idempotency_key),
    CONSTRAINT fk_program_reminder_run_program FOREIGN KEY (program_id)
        REFERENCES evaluation_program(id) ON DELETE CASCADE
);

CREATE INDEX ix_program_reminder_run_tenant_program_created
    ON program_reminder_run(tenant_id, program_id, created_at DESC);

ALTER TABLE program_notification
    ADD COLUMN reminder_run_id UUID,
    ADD COLUMN reminder_idempotency_key UUID,
    ADD COLUMN participant_id UUID,
    ADD COLUMN reminder_episode_key VARCHAR(64),
    ADD COLUMN reminder_dedupe_key VARCHAR(64),
    ADD COLUMN reminder_on DATE,
    ADD COLUMN reminder_policy_version VARCHAR(60),
    ADD COLUMN reminder_stage VARCHAR(30),
    ADD COLUMN reminder_action VARCHAR(50),
    ADD COLUMN reminder_round INTEGER,
    ADD COLUMN deep_link VARCHAR(500),
    ADD COLUMN source_snapshot_json JSONB;

ALTER TABLE program_notification
    ADD CONSTRAINT fk_program_notification_reminder_run FOREIGN KEY (reminder_run_id)
        REFERENCES program_reminder_run(id),
    ADD CONSTRAINT fk_program_notification_participant FOREIGN KEY (participant_id)
        REFERENCES program_participant(id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_program_notification_reminder_stage CHECK (
        reminder_stage IS NULL OR reminder_stage IN ('GOAL','INTERMEDIATE','SELF_REVIEW','REVIEW','CALIBRATION','FEEDBACK')),
    ADD CONSTRAINT ck_program_notification_reminder_action CHECK (
        reminder_action IS NULL OR reminder_action IN (
            'GOAL_AUTHOR','GOAL_APPROVAL','GOAL_SELF_REPORT','INTERMEDIATE_CHECK','SELF_REVIEW',
            'REVIEW','CALIBRATION','FEEDBACK_DELIVERY','FEEDBACK_ACKNOWLEDGEMENT','FEEDBACK_RESOLUTION')),
    ADD CONSTRAINT ck_program_notification_reminder_shape CHECK (
        reminder_dedupe_key IS NULL OR (
            channel = 'IN_APP' AND reminder_run_id IS NOT NULL AND reminder_idempotency_key IS NOT NULL
            AND participant_id IS NOT NULL AND reminder_episode_key IS NOT NULL
            AND reminder_on IS NOT NULL AND reminder_policy_version IS NOT NULL
            AND reminder_stage IS NOT NULL AND reminder_action IS NOT NULL
            AND reminder_round IS NOT NULL AND deep_link IS NOT NULL AND source_snapshot_json IS NOT NULL));

CREATE UNIQUE INDEX uq_program_notification_tenant_reminder_dedupe
    ON program_notification(tenant_id, reminder_dedupe_key)
    WHERE reminder_dedupe_key IS NOT NULL;

CREATE INDEX ix_program_notification_tenant_program_reminder_created
    ON program_notification(tenant_id, program_id, reminder_on DESC, created_at DESC)
    WHERE reminder_dedupe_key IS NOT NULL;
