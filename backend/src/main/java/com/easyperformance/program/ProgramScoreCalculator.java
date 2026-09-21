package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ScoreCalculationResult;
import com.easyperformance.program.ProgramDtos.ScoreContribution;
import com.easyperformance.program.ProgramDtos.ScaleDefinitionInput;
import com.easyperformance.program.ProgramDtos.ReviewItemAnswerInput;
import com.easyperformance.program.ProgramTypes.ScaleKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Deterministic, explainable score primitives. */
public final class ProgramScoreCalculator {
    private ProgramScoreCalculator() {}

    /** Grade-only scales cannot be bypassed by sending an arbitrary numeric score. */
    public static BigDecimal inputScore(ScaleDefinitionInput scale, ReviewItemAnswerInput answer) {
        boolean hasCode = answer.scaleCode() != null && !answer.scaleCode().isBlank();
        if (answer.numericScore() != null) {
            if (hasCode || scale.kind() == ScaleKind.GRADE) {
                throw new ProgramRuleViolation("SCALE_INPUT", "use the configured grade choice without numericScore");
            }
            return answer.numericScore();
        }
        if (!hasCode) throw new ProgramRuleViolation("SCORE_REQUIRED", "a score or grade choice is required");
        return scale.levels().stream().filter(level -> level.code().equals(answer.scaleCode()))
            .map(com.easyperformance.program.ProgramDtos.ScaleLevelInput::convertedScore)
            .filter(java.util.Objects::nonNull).findFirst()
            .orElseThrow(() -> new ProgramRuleViolation("SCALE_CODE", "grade choice is not in the configured scale"));
    }

    public static ScoreCalculationResult weightedAverage(List<ScoreContribution> contributions) {
        BigDecimal denominator = contributions.stream().map(ScoreContribution::weightPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            throw new ProgramRuleViolation("ZERO_DENOMINATOR", "score denominator must be greater than zero");
        }
        BigDecimal numerator = contributions.stream()
            .map(c -> c.score().multiply(c.weightPercent()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ScoreCalculationResult(numerator.divide(denominator, 2, RoundingMode.HALF_UP), List.copyOf(contributions));
    }
}
