package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ScoreContribution;
import com.easyperformance.program.ProgramTypes.AdjustmentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramScoreCalculatorTest {

    @Test
    void calculatesWeightedScoreAndRetainsTheExplainableBreakdown() {
        var result = ProgramScoreCalculator.weightedAverage(List.of(
            new ScoreContribution("SELF", new BigDecimal("80"), new BigDecimal("20")),
            new ScoreContribution("MANAGER_1", new BigDecimal("90"), new BigDecimal("80"))));

        assertThat(result.score()).isEqualByComparingTo("88.00");
        assertThat(result.contributions()).hasSize(2);
    }

    @Test
    void refusesZeroDenominatorInsteadOfManufacturingAZeroScore() {
        assertThatThrownBy(() -> ProgramScoreCalculator.weightedAverage(List.of()))
            .isInstanceOf(ProgramRuleViolation.class)
            .hasMessageContaining("denominator");
    }

    @Test
    void emptyNormalizationPopulationIsSkippedWithAnExplainableWarning() {
        assertThat(ProgramExecutionService.normalizationPopulationWarning(AdjustmentMethod.MEAN, 0))
            .isEqualTo("NO_ADJUSTMENT_TARGETS");
        assertThat(ProgramExecutionService.normalizationPopulationWarning(AdjustmentMethod.NONE, 0))
            .isNull();
    }

    @Test
    void gradeInputCannotBypassConfiguredChoicesWithAnArbitraryNumber() {
        var config = ProgramConfigurationFactory.defaults(ProgramTypes.EvaluationKind.PERFORMANCE);
        var scale = config.scales().getFirst();
        var itemId = java.util.UUID.randomUUID();
        assertThat(scale.kind()).isEqualTo(ProgramTypes.ScaleKind.GRADE);
        assertThat(ProgramScoreCalculator.inputScore(scale,
            new ProgramDtos.ReviewItemAnswerInput(itemId, "4", null, "evidence")))
            .isEqualByComparingTo("80");
        assertThatThrownBy(() -> ProgramScoreCalculator.inputScore(scale,
            new ProgramDtos.ReviewItemAnswerInput(itemId, null, new BigDecimal("97"), "bypass")))
            .isInstanceOf(ProgramRuleViolation.class);
        assertThatThrownBy(() -> ProgramScoreCalculator.inputScore(scale,
            new ProgramDtos.ReviewItemAnswerInput(itemId, "4", new BigDecimal("97"), "ambiguous")))
            .isInstanceOf(ProgramRuleViolation.class);
    }
}
