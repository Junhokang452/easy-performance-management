package com.easyperformance.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Workspace metadata for a KPI actual recorded as a goal check-in.
 *
 * <p>The KPI actual remains the append-only source of truth for the measured value. This row only
 * stores the workflow-specific progress percentage, keyed one-to-one by that actual's id.
 */
@Entity
@Table(name = "evaluation_goal_check_in", indexes = {
    @Index(name = "ix_eval_goal_check_in_tenant_goal", columnList = "tenant_id, goal_id")
})
public class EvaluationGoalCheckIn {
    @Id
    @Column(name = "kpi_actual_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID kpiActualId;

    @Column(name = "tenant_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "goal_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "progress_percent", precision = 5, scale = 2)
    private BigDecimal progressPercent;

    public UUID getKpiActualId() { return kpiActualId; }
    public void setKpiActualId(UUID kpiActualId) { this.kpiActualId = kpiActualId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }
    public BigDecimal getProgressPercent() { return progressPercent; }
    public void setProgressPercent(BigDecimal progressPercent) { this.progressPercent = progressPercent; }
}
