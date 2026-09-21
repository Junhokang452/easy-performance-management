package com.easyperformance.program;
import com.easyperformance.program.ProgramTypes.ProgramStatus;import jakarta.persistence.LockModeType;import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface EvaluationProgramRepository extends JpaRepository<EvaluationProgram,UUID>{
 Optional<EvaluationProgram> findByIdAndTenantId(UUID id,UUID tenantId);
 Page<EvaluationProgram> findAllByTenantIdOrderByEvaluationYearDescCreatedAtDesc(UUID tenantId,Pageable pageable);
 Page<EvaluationProgram> findAllByTenantIdAndStatusInOrderByEvaluationYearDescCreatedAtDesc(UUID tenantId,Collection<ProgramStatus> statuses,Pageable pageable);
 boolean existsByTenantIdAndEvaluationYearAndName(UUID tenantId,Integer year,String name);
 boolean existsByTenantIdAndEvaluationYearAndNameAndIdNot(UUID tenantId,Integer year,String name,UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select p from EvaluationProgram p where p.id=:id and p.tenantId=:tenantId")Optional<EvaluationProgram> findLocked(@Param("id")UUID id,@Param("tenantId")UUID tenantId);
}
