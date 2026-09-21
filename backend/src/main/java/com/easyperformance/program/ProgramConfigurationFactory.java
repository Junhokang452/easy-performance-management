package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyware.platform.UuidV7;
import java.math.BigDecimal;
import java.util.*;

final class ProgramConfigurationFactory {
    private ProgramConfigurationFactory() {}

    static ProgramConfiguration defaults(EvaluationKind kind) {
        UUID input = UuidV7.generate(), result = UuidV7.generate(), group = UuidV7.generate();
        var inputScale = new ScaleDefinitionInput(input, "1-5 입력척도", ScaleUse.INPUT, ScaleKind.GRADE,
            List.of(level("1","1",20),level("2","2",40),level("3","3",60),level("4","4",80),level("5","5",100)));
        var resultScale = new ScaleDefinitionInput(result, "S-A-B-C-D 결과척도", ScaleUse.RESULT, ScaleKind.GRADE,
            List.of(new ScaleLevelInput("S","S",new BigDecimal("100"),new BigDecimal("95"),new BigDecimal("100"),null),
                new ScaleLevelInput("A","A",new BigDecimal("90"),new BigDecimal("85"),new BigDecimal("95"),null),
                new ScaleLevelInput("B","B",new BigDecimal("80"),new BigDecimal("75"),new BigDecimal("85"),null),
                new ScaleLevelInput("C","C",new BigDecimal("70"),new BigDecimal("65"),new BigDecimal("75"),null),
                new ScaleLevelInput("D","D",new BigDecimal("60"),null,new BigDecimal("65"),null)));
        var stages = List.of(
            new StageDefinitionInput(ProgramStage.GOAL,true,null,null,FormMode.DEFINITION_AND_ACHIEVEMENT_LEVELS,true,false,null),
            new StageDefinitionInput(ProgramStage.INTERMEDIATE,true,null,null,FormMode.DEFINITION_ONLY,true,false,null),
            new StageDefinitionInput(ProgramStage.SELF_REVIEW,true,null,null,FormMode.DEFINITION_AND_ACHIEVEMENT_LEVELS,true,false,null),
            new StageDefinitionInput(ProgramStage.REVIEW,true,null,null,FormMode.DEFINITION_AND_ACHIEVEMENT_LEVELS,true,true,null),
            new StageDefinitionInput(ProgramStage.CALCULATION,true,null,null,null,false,false,null),
            new StageDefinitionInput(ProgramStage.CALIBRATION,true,null,null,null,false,false,null),
            new StageDefinitionInput(ProgramStage.FEEDBACK,true,null,null,FormMode.DEFINITION_ONLY,true,false,null));
        var weights = List.of(new ReviewerWeightPlanInput(1,Map.of(1,new BigDecimal("100")),BigDecimal.ZERO));
        var groupDef = new RevieweeGroupInput(group,"전체 구성원","기본 그룹",ItemAssignmentMode.AGREEMENT,
            EvaluationMethod.ABSOLUTE,true,true,0,List.of(),weights);
        String component = kind == EvaluationKind.COMPETENCY ? "COMPETENCY" : "PERFORMANCE";
        var calculation = new CalculationPolicyInput(input,result,null,false,AdjustmentTarget.ALL,
            AdjustmentMethod.NONE,PopulationBasis.DEPARTMENT,null,null,
            List.of(new ComponentWeightInput(component,new BigDecimal("100"))),2);
        var publication = new PublicationPolicyInput(PreviousRoundVisibility.HIDDEN,false,
            MemberResultVisibility.SCORE_AND_GRADE,true,null,null,1,false,true,true);
        return new ProgramConfiguration(GoalMode.AGREEMENT,stages,List.of(inputScale,resultScale),calculation,
            publication,List.of(groupDef),List.of(),List.of(),List.of());
    }

    static ProgramConfiguration normalize(ProgramConfiguration c) {
        List<ScaleDefinitionInput> scales=c.scales().stream().map(s->new ScaleDefinitionInput(
            s.id(),s.name(),s.use(),s.kind(),List.copyOf(s.levels()))).toList();
        List<RevieweeGroupInput> groups=c.groups().stream().map(g->new RevieweeGroupInput(
            g.id(),g.name(),g.definition(),g.itemAssignmentMode(),g.evaluationMethod(),
            g.intermediateEnabled(),g.selfReviewEnabled(),g.priority(),List.copyOf(g.conditions()),List.copyOf(g.reviewerWeightPlans()))).toList();
        return new ProgramConfiguration(c.goalMode(),List.copyOf(c.stages()),scales,c.calculation(),c.publication(),groups,
            List.copyOf(c.commonItems()),List.copyOf(c.departmentPerformanceGroups()),List.copyOf(c.allocationRows()));
    }
    private static ScaleLevelInput level(String code,String label,int value){return new ScaleLevelInput(code,label,new BigDecimal(value),null,null,null);}
}
