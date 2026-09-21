package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyware.platform.UuidV7;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProgramGroupMatcherTest {
    @Test
    void overlappingConditionsSelectTheLowestPriorityNumber() {
        UUID low=UuidV7.generate(), high=UuidV7.generate();
        var weights=List.of(new ReviewerWeightPlanInput(1,Map.of(1,new BigDecimal("100")),BigDecimal.ZERO));
        var lower=new RevieweeGroupInput(low,"all",null,ItemAssignmentMode.AGREEMENT,EvaluationMethod.ABSOLUTE,true,true,10,List.of(),weights);
        var higher=new RevieweeGroupInput(high,"engineering",null,ItemAssignmentMode.AGREEMENT,EvaluationMethod.ABSOLUTE,true,true,1,List.of(new GroupConditionInput(ConditionField.JOB,ConditionOperator.IN,List.of("ENG"))),weights);
        var employee=new ParticipantAttributes(UuidV7.generate(),"E1","Kim",null,null,null,null,null,"ENG","ACTIVE");

        assertThat(ProgramGroupMatcher.match(List.of(lower,higher),employee).orElseThrow().id()).isEqualTo(high);
    }
}
