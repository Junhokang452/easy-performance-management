-- Preserve workflow-specific check-in progress without changing the append-only KPI actual contract.
CREATE TABLE evaluation_goal_check_in (
    kpi_actual_id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    goal_id UUID NOT NULL,
    progress_percent NUMERIC(5,2),
    CONSTRAINT ck_eval_goal_check_in_progress
        CHECK (progress_percent IS NULL OR (progress_percent >= 0 AND progress_percent <= 100)),
    CONSTRAINT fk_eval_goal_check_in_actual
        FOREIGN KEY (kpi_actual_id) REFERENCES kpi_actual(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_goal_check_in_goal
        FOREIGN KEY (goal_id) REFERENCES evaluation_goal(id) ON DELETE CASCADE
);

CREATE INDEX ix_eval_goal_check_in_tenant_goal
    ON evaluation_goal_check_in (tenant_id, goal_id);
