package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.GoalStatus;
import com.easyware.platform.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="program_goal", indexes=@Index(name="ix_program_goal_tenant_participant",columnList="tenant_id, participant_id, status"))
public class ProgramGoal extends TenantAwareAuditEntity {
 @Id @Column(columnDefinition="uuid",nullable=false,updatable=false) private UUID id; @Column(name="program_id",columnDefinition="uuid",nullable=false) private UUID programId; @Column(name="participant_id",columnDefinition="uuid",nullable=false) private UUID participantId;
 @Column(name="catalog_item_id",columnDefinition="uuid") private UUID catalogItemId; @Column(name="department_goal_id",columnDefinition="uuid") private UUID departmentGoalId; @Column(nullable=false,length=200) private String title; @Column(nullable=false,columnDefinition="text") private String definition;
 @Column(name="weight_percent",precision=7,scale=4,nullable=false) private BigDecimal weightPercent; @Column(name="target_value",precision=18,scale=4) private BigDecimal targetValue; @Column(length=20) private String unit;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="achievement_levels",columnDefinition="jsonb",nullable=false) private String achievementLevels; @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private GoalStatus status;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="evidence_json",columnDefinition="jsonb",nullable=false) private String evidenceJson;
 @Column(name="draft_opinion",columnDefinition="text") private String draftOpinion; @Column(name="decision_opinion",columnDefinition="text") private String decisionOpinion; @Column(name="achieved_level_code",length=30) private String achievedLevelCode; @Column(name="achievement_summary",columnDefinition="text") private String achievementSummary;
 @Column(nullable=false) private int revision; @Version @Column(name="row_version",nullable=false) private long rowVersion;
 @PrePersist void prePersist(){if(id==null)id=UuidV7.generate();if(status==null)status=GoalStatus.DRAFT;if(revision==0)revision=1;if(evidenceJson==null)evidenceJson="[]";}
 public UUID getId(){return id;}public void setId(UUID v){id=v;}public UUID getProgramId(){return programId;}public void setProgramId(UUID v){programId=v;}public UUID getParticipantId(){return participantId;}public void setParticipantId(UUID v){participantId=v;}
 public UUID getCatalogItemId(){return catalogItemId;}public void setCatalogItemId(UUID v){catalogItemId=v;}public UUID getDepartmentGoalId(){return departmentGoalId;}public void setDepartmentGoalId(UUID v){departmentGoalId=v;}public String getTitle(){return title;}public void setTitle(String v){title=v;}public String getDefinition(){return definition;}public void setDefinition(String v){definition=v;}
 public BigDecimal getWeightPercent(){return weightPercent;}public void setWeightPercent(BigDecimal v){weightPercent=v;}public BigDecimal getTargetValue(){return targetValue;}public void setTargetValue(BigDecimal v){targetValue=v;}public String getUnit(){return unit;}public void setUnit(String v){unit=v;}public String getAchievementLevels(){return achievementLevels;}public void setAchievementLevels(String v){achievementLevels=v;}
 public GoalStatus getStatus(){return status;}public void setStatus(GoalStatus v){status=v;}public String getEvidenceJson(){return evidenceJson;}public void setEvidenceJson(String v){evidenceJson=v;}public String getDraftOpinion(){return draftOpinion;}public void setDraftOpinion(String v){draftOpinion=v;}public String getDecisionOpinion(){return decisionOpinion;}public void setDecisionOpinion(String v){decisionOpinion=v;}public String getAchievedLevelCode(){return achievedLevelCode;}public void setAchievedLevelCode(String v){achievedLevelCode=v;}public String getAchievementSummary(){return achievementSummary;}public void setAchievementSummary(String v){achievementSummary=v;}public int getRevision(){return revision;}public void setRevision(int v){revision=v;}public long getRowVersion(){return rowVersion;}
}
