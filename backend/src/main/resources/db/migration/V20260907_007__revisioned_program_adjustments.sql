ALTER TABLE program_adjustment ADD COLUMN revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE program_adjustment DROP CONSTRAINT uq_program_adjustment_participant;
ALTER TABLE program_adjustment ADD CONSTRAINT uq_program_adjustment_revision
    UNIQUE (tenant_id, participant_id, revision);
CREATE INDEX ix_program_adjustment_tenant_participant_revision
    ON program_adjustment (tenant_id, participant_id, revision DESC);
