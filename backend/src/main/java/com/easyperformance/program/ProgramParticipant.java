package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramStage;
import com.easyperformance.program.ProgramTypes.ProgramStageStatus;
import com.easyware.platform.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="program_participant", uniqueConstraints=@UniqueConstraint(name="uq_program_participant_assignment", columnNames={"tenant_id","program_id","employee_id","assignment_key"}),
    indexes={@Index(name="ix_program_participant_tenant_program_status",columnList="tenant_id, program_id, status"),@Index(name="ix_program_participant_tenant_employee",columnList="tenant_id, employee_id")})
public class ProgramParticipant extends TenantAwareAuditEntity {
    @Id @Column(columnDefinition="uuid",nullable=false,updatable=false) private UUID id;
    @Column(name="program_id",columnDefinition="uuid",nullable=false) private UUID programId;
    @Column(name="employee_id",columnDefinition="uuid",nullable=false) private UUID employeeId;
    @Column(name="assignment_id",columnDefinition="uuid") private UUID assignmentId;
    @Column(name="assignment_key",nullable=false,length=80) private String assignmentKey;
    @Column(name="org_unit_id",columnDefinition="uuid") private UUID orgUnitId;
    @Column(name="group_id",columnDefinition="uuid") private UUID groupId;
    @Column(name="group_name",length=100) private String groupName;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="attributes_json",columnDefinition="jsonb",nullable=false) private String attributesJson;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ParticipantStatus status;
    @Column(name="weight_percent",precision=7,scale=4,nullable=false) private BigDecimal weightPercent;
    @Enumerated(EnumType.STRING) @Column(name="current_stage",length=30) private ProgramStage currentStage;
    @Enumerated(EnumType.STRING) @Column(name="stage_status",nullable=false,length=20) private ProgramStageStatus stageStatus;
    @Column(name="current_round",nullable=false) private int currentRound;
    @Column(name="result_published",nullable=false) private boolean resultPublished;
    @Column(name="exclusion_reason",length=500) private String exclusionReason;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
    @PrePersist void prePersist(){if(id==null)id=UuidV7.generate();if(status==null)status=ParticipantStatus.ACTIVE;if(weightPercent==null)weightPercent=new BigDecimal("100");if(stageStatus==null)stageStatus=ProgramStageStatus.NOT_STARTED;}
    public UUID getId(){return id;} public void setId(UUID v){id=v;} public UUID getProgramId(){return programId;} public void setProgramId(UUID v){programId=v;}
    public UUID getEmployeeId(){return employeeId;} public void setEmployeeId(UUID v){employeeId=v;} public UUID getAssignmentId(){return assignmentId;} public void setAssignmentId(UUID v){assignmentId=v;}
    public String getAssignmentKey(){return assignmentKey;} public void setAssignmentKey(String v){assignmentKey=v;} public UUID getOrgUnitId(){return orgUnitId;} public void setOrgUnitId(UUID v){orgUnitId=v;}
    public UUID getGroupId(){return groupId;} public void setGroupId(UUID v){groupId=v;} public String getGroupName(){return groupName;} public void setGroupName(String v){groupName=v;}
    public String getAttributesJson(){return attributesJson;} public void setAttributesJson(String v){attributesJson=v;} public ParticipantStatus getStatus(){return status;} public void setStatus(ParticipantStatus v){status=v;}
    public BigDecimal getWeightPercent(){return weightPercent;} public void setWeightPercent(BigDecimal v){weightPercent=v;} public ProgramStage getCurrentStage(){return currentStage;} public void setCurrentStage(ProgramStage v){currentStage=v;}
    public ProgramStageStatus getStageStatus(){return stageStatus;} public void setStageStatus(ProgramStageStatus v){stageStatus=v;} public int getCurrentRound(){return currentRound;} public void setCurrentRound(int v){currentRound=v;}
    public boolean isResultPublished(){return resultPublished;} public void setResultPublished(boolean v){resultPublished=v;} public String getExclusionReason(){return exclusionReason;} public void setExclusionReason(String v){exclusionReason=v;} public long getRowVersion(){return rowVersion;}
}
