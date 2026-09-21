package com.easyperformance.program;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface EvaluationProgramRevisionRepository extends JpaRepository<EvaluationProgramRevision,UUID>{List<EvaluationProgramRevision> findAllByTenantIdAndProgramIdOrderByRevisionDesc(UUID tenantId,UUID programId);}
