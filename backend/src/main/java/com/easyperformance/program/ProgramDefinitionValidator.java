package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.AllocationRowInput;
import com.easyperformance.program.ProgramDtos.ProgramConfiguration;
import com.easyperformance.program.ProgramDtos.ReviewerWeightPlanInput;
import com.easyperformance.program.ProgramDtos.ScaleDefinitionInput;
import com.easyperformance.program.ProgramTypes.GoalMode;
import com.easyperformance.program.ProgramTypes.ProgramStage;
import com.easyperformance.program.ProgramTypes.ScaleUse;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProgramDefinitionValidator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private ProgramDefinitionValidator() {}

    public static void validate(ProgramConfiguration configuration) {
        if (configuration == null) throw new ProgramRuleViolation("CONFIG_REQUIRED", "configuration is required");
        List<ProgramStage> enabled = configuration.stages().stream().filter(s -> s.enabled()).map(s -> s.stage()).toList();
        validateStageOrder(enabled);
        if (configuration.goalMode() == GoalMode.SELF_REPORT && enabled.contains(ProgramStage.INTERMEDIATE)) {
            throw new ProgramRuleViolation("SELF_REPORT_INTERMEDIATE_CONFLICT",
                "intermediate stage must be disabled for self-report goals");
        }
        configuration.groups().forEach(group -> group.reviewerWeightPlans().forEach(ProgramDefinitionValidator::validateWeightPlan));
        validateAllocationRows(configuration.allocationRows());
        validateScaleReferences(configuration);
        validateAllocationGradeCodes(configuration);
        validateCommonItemWeights(configuration);
        validateComponentWeights(configuration);
    }

    public static void validateStageOrder(List<ProgramStage> stages) {
        Set<ProgramStage> unique = new HashSet<>(stages);
        if (unique.size() != stages.size()) throw new ProgramRuleViolation("DUPLICATE_STAGE", "duplicate stage is not allowed");
        int previous = -1;
        for (ProgramStage stage : stages) {
            int current = canonicalOrder(stage);
            if (current <= previous) throw new ProgramRuleViolation("INVALID_STAGE_ORDER", "stage order is invalid");
            previous = current;
        }
    }

    public static void validateWeightPlan(ReviewerWeightPlanInput plan) {
        if (plan.reviewerWeights().size() != plan.actualReviewerCount()) {
            throw new ProgramRuleViolation("REVIEWER_WEIGHT_COUNT", "reviewer weight count does not match actualReviewerCount");
        }
        for (int round = 1; round <= plan.actualReviewerCount(); round++) {
            if (!plan.reviewerWeights().containsKey(round)) {
                throw new ProgramRuleViolation("REVIEWER_WEIGHT_ROUND", "reviewer weight rounds must be contiguous from 1");
            }
        }
        BigDecimal sum = plan.reviewerWeights().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(plan.departmentWeight() == null ? BigDecimal.ZERO : plan.departmentWeight());
        if (sum.compareTo(ONE_HUNDRED) != 0) {
            throw new ProgramRuleViolation("REVIEWER_WEIGHT_SUM", "reviewer and department weights must total 100");
        }
    }

    public static void validateAllocationRows(List<AllocationRowInput> rows) {
        Set<Integer> populations = new HashSet<>();
        for (AllocationRowInput row : rows) {
            if (!populations.add(row.populationSize())) {
                throw new ProgramRuleViolation("DUPLICATE_POPULATION", "duplicate allocation population");
            }
            if (row.gradeHeadcounts().values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new ProgramRuleViolation("ALLOCATION_HEADCOUNT", "grade headcounts must be non-negative integers");
            }
            int sum = row.gradeHeadcounts().values().stream().mapToInt(Integer::intValue).sum();
            if (sum != row.populationSize()) {
                throw new ProgramRuleViolation("ALLOCATION_SUM", "grade headcounts must equal population size");
            }
        }
    }

    private static void validateScaleReferences(ProgramConfiguration c) {
        ScaleDefinitionInput input = scale(c, c.calculation().inputScaleId(), "input");
        if (input.use() != ScaleUse.INPUT) {
            throw new ProgramRuleViolation("INPUT_SCALE_USE", "inputScaleId must reference an INPUT scale");
        }
        ScaleDefinitionInput result = scale(c, c.calculation().resultScaleId(), "result");
        if (result.use() != ScaleUse.RESULT) {
            throw new ProgramRuleViolation("RESULT_SCALE_USE", "resultScaleId must reference a RESULT scale");
        }
        if (c.calculation().departmentPerformanceEnabled() && c.calculation().departmentResultScaleId() == null) {
            throw new ProgramRuleViolation("DEPARTMENT_SCALE_REQUIRED",
                "departmentResultScaleId is required when department performance is enabled");
        }
        if (c.calculation().departmentResultScaleId() != null) {
            ScaleDefinitionInput department = scale(c, c.calculation().departmentResultScaleId(), "department result");
            if (department.use() != ScaleUse.DEPARTMENT_RESULT) {
                throw new ProgramRuleViolation("DEPARTMENT_SCALE_USE",
                    "departmentResultScaleId must reference a DEPARTMENT_RESULT scale");
            }
        }
    }

    private static ScaleDefinitionInput scale(ProgramConfiguration c, java.util.UUID id, String purpose) {
        return c.scales().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new ProgramRuleViolation("SCALE_REFERENCE", purpose + " scale must exist"));
    }

    private static void validateAllocationGradeCodes(ProgramConfiguration c) {
        Set<String> resultCodes = scale(c, c.calculation().resultScaleId(), "result").levels().stream()
            .map(ProgramDtos.ScaleLevelInput::code).collect(java.util.stream.Collectors.toSet());
        c.allocationRows().forEach(row -> row.gradeHeadcounts().keySet().forEach(code -> {
            if (!resultCodes.contains(code)) {
                throw new ProgramRuleViolation("ALLOCATION_GRADE_CODE",
                    "allocation grade code must exist in the result scale: " + code);
            }
        }));
    }

    private static void validateCommonItemWeights(ProgramConfiguration c) {
        Map<String, BigDecimal> sums = new java.util.HashMap<>();
        c.commonItems().forEach(item -> sums.merge(item.groupId() + ":" + item.round(),
            item.weightPercent(), BigDecimal::add));
        sums.forEach((key, sum) -> {
            if (sum.compareTo(ONE_HUNDRED) != 0) {
                throw new ProgramRuleViolation("COMMON_ITEM_WEIGHT_SUM",
                    "common item weights must total 100 for group and round " + key);
            }
        });
    }

    private static void validateComponentWeights(ProgramConfiguration c) {
        BigDecimal sum = c.calculation().componentWeights().stream()
            .map(ProgramDtos.ComponentWeightInput::weightPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(ONE_HUNDRED) != 0) {
            throw new ProgramRuleViolation("COMPONENT_WEIGHT_SUM", "calculation component weights must total 100");
        }
    }

    private static int canonicalOrder(ProgramStage stage) {
        return switch (stage) {
            case GOAL -> 0;
            case INTERMEDIATE -> 1;
            case SELF_REVIEW -> 2;
            case REVIEW -> 3;
            case CALCULATION -> 4;
            case CALIBRATION -> 5;
            case FEEDBACK -> 6;
        };
    }
}
