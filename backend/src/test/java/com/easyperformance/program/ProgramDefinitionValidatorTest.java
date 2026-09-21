package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramTypes.EvaluationKind;
import com.easyperformance.program.ProgramTypes.ProgramStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramDefinitionValidatorTest {

    @Test
    void reviewerAndDepartmentWeightsMustTotalOneHundredForEveryHeadcountCase() {
        var invalid = new ReviewerWeightPlanInput(2,
            Map.of(1, new BigDecimal("40"), 2, new BigDecimal("40")), new BigDecimal("10"));

        assertThatThrownBy(() -> ProgramDefinitionValidator.validateWeightPlan(invalid))
            .isInstanceOf(ProgramRuleViolation.class)
            .hasMessageContaining("100");
    }

    @Test
    void everyAllocationRowMustAllocateExactlyItsPopulation() {
        var invalid = new AllocationRowInput(5, Map.of("S", 1, "A", 1, "B", 1, "C", 1));

        assertThatThrownBy(() -> ProgramDefinitionValidator.validateAllocationRows(List.of(invalid)))
            .isInstanceOf(ProgramRuleViolation.class)
            .hasMessageContaining("population");
    }

    @Test
    void stagesCannotContainDuplicateKinds() {
        assertThatThrownBy(() -> ProgramDefinitionValidator.validateStageOrder(
            List.of(ProgramStage.GOAL, ProgramStage.SELF_REVIEW, ProgramStage.SELF_REVIEW)))
            .isInstanceOf(ProgramRuleViolation.class)
            .hasMessageContaining("duplicate");
    }

    @Test
    void departmentPerformanceRequiresItsOwnReferencedResultScale() {
        ProgramConfiguration defaults = ProgramConfigurationFactory.defaults(EvaluationKind.PERFORMANCE);
        CalculationPolicyInput calculation = defaults.calculation();
        ProgramConfiguration invalid = withCalculation(defaults, new CalculationPolicyInput(
            calculation.inputScaleId(), calculation.resultScaleId(), null, true,
            calculation.adjustmentTarget(), calculation.adjustmentMethod(), calculation.populationBasis(),
            calculation.targetMean(), calculation.targetStandardDeviation(), calculation.componentWeights(),
            calculation.decimalPlaces()));

        assertThatThrownBy(() -> ProgramDefinitionValidator.validate(invalid))
            .isInstanceOf(ProgramRuleViolation.class)
            .extracting(error -> ((ProgramRuleViolation) error).rule())
            .isEqualTo("DEPARTMENT_SCALE_REQUIRED");
    }

    @Test
    void allocationGradesMustExistInTheConfiguredResultScale() {
        ProgramConfiguration defaults = ProgramConfigurationFactory.defaults(EvaluationKind.PERFORMANCE);
        ProgramConfiguration invalid = new ProgramConfiguration(defaults.goalMode(), defaults.stages(),
            defaults.scales(), defaults.calculation(), defaults.publication(), defaults.groups(),
            defaults.commonItems(), defaults.departmentPerformanceGroups(),
            List.of(new AllocationRowInput(2, Map.of("UNKNOWN", 2))));

        assertThatThrownBy(() -> ProgramDefinitionValidator.validate(invalid))
            .isInstanceOf(ProgramRuleViolation.class)
            .extracting(error -> ((ProgramRuleViolation) error).rule())
            .isEqualTo("ALLOCATION_GRADE_CODE");
    }

    private ProgramConfiguration withCalculation(ProgramConfiguration defaults, CalculationPolicyInput calculation) {
        return new ProgramConfiguration(defaults.goalMode(), defaults.stages(), defaults.scales(), calculation,
            defaults.publication(), defaults.groups(), defaults.commonItems(),
            defaults.departmentPerformanceGroups(), defaults.allocationRows());
    }
}
