package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.AssignmentStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.program.ProgramTypes.ReviewerAssignmentOrigin;
import com.easyware.platform.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;

@Entity @Table(name="program_reviewer_assignment", uniqueConstraints=@UniqueConstraint(name="uq_program_reviewer",columnNames={"tenant_id","participant_id","reviewer_employee_id","reviewer_role","review_round"}),
 indexes={@Index(name="ix_program_reviewer_tenant_participant",columnList="tenant_id, participant_id"),@Index(name="ix_program_reviewer_tenant_employee",columnList="tenant_id, reviewer_employee_id, status")})
public class ProgramReviewerAssignment extends TenantAwareAuditEntity {
 @Id @Column(columnDefinition="uuid",nullable=false,updatable=false) private UUID id;
 @Column(name="program_id",columnDefinition="uuid",nullable=false) private UUID programId; @Column(name="participant_id",columnDefinition="uuid",nullable=false) private UUID participantId;
 @Column(name="reviewer_employee_id",columnDefinition="uuid",nullable=false) private UUID reviewerEmployeeId; @Enumerated(EnumType.STRING) @Column(name="reviewer_role",nullable=false,length=30) private ReviewerRole role;
 @Column(name="review_round",nullable=false) private int round; @Column(name="weight_percent",precision=7,scale=4,nullable=false) private BigDecimal weightPercent;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AssignmentStatus status; @Version @Column(name="row_version",nullable=false) private long rowVersion;
 @Enumerated(EnumType.STRING) @Column(name="assignment_origin",nullable=false,length=20) private ReviewerAssignmentOrigin assignmentOrigin;
 @Column(name="source_assignment_id",columnDefinition="uuid") private UUID sourceAssignmentId;
 @Column(name="source_version") private Long sourceVersion;
 @Column(name="source_as_of_date") private LocalDate sourceAsOfDate;
 @Column(name="automation_run_id",columnDefinition="uuid") private UUID automationRunId;
 @PrePersist void prePersist(){if(id==null)id=UuidV7.generate();if(status==null)status=AssignmentStatus.ASSIGNED;if(weightPercent==null)weightPercent=BigDecimal.ZERO;if(assignmentOrigin==null)assignmentOrigin=ReviewerAssignmentOrigin.MANUAL;}
 public UUID getId(){return id;} public void setId(UUID v){id=v;} public UUID getProgramId(){return programId;} public void setProgramId(UUID v){programId=v;} public UUID getParticipantId(){return participantId;} public void setParticipantId(UUID v){participantId=v;}
 public UUID getReviewerEmployeeId(){return reviewerEmployeeId;} public void setReviewerEmployeeId(UUID v){reviewerEmployeeId=v;} public ReviewerRole getRole(){return role;} public void setRole(ReviewerRole v){role=v;} public int getRound(){return round;} public void setRound(int v){round=v;}
 public BigDecimal getWeightPercent(){return weightPercent;} public void setWeightPercent(BigDecimal v){weightPercent=v;} public AssignmentStatus getStatus(){return status;} public void setStatus(AssignmentStatus v){status=v;}
 public ReviewerAssignmentOrigin getAssignmentOrigin(){return assignmentOrigin;} public void setAssignmentOrigin(ReviewerAssignmentOrigin v){assignmentOrigin=v;}
 public UUID getSourceAssignmentId(){return sourceAssignmentId;} public void setSourceAssignmentId(UUID v){sourceAssignmentId=v;} public Long getSourceVersion(){return sourceVersion;} public void setSourceVersion(Long v){sourceVersion=v;}
 public LocalDate getSourceAsOfDate(){return sourceAsOfDate;} public void setSourceAsOfDate(LocalDate v){sourceAsOfDate=v;} public UUID getAutomationRunId(){return automationRunId;} public void setAutomationRunId(UUID v){automationRunId=v;}
 public long getRowVersion(){return rowVersion;}
}
