package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.GroupConditionInput;
import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.RevieweeGroupInput;
import com.easyperformance.program.ProgramTypes.ConditionField;
import com.easyperformance.program.ProgramTypes.ConditionOperator;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ProgramGroupMatcher {
    private ProgramGroupMatcher() {}
    public static Optional<RevieweeGroupInput> match(List<RevieweeGroupInput> groups, ParticipantAttributes attributes) {
        return groups.stream().sorted(Comparator.comparingInt(RevieweeGroupInput::priority))
            .filter(group -> group.conditions().stream().allMatch(c -> matches(c, attributes))).findFirst();
    }
    private static boolean matches(GroupConditionInput c, ParticipantAttributes a) {
        String actual = value(c.field(), a);
        boolean contains = actual != null && c.values().stream().anyMatch(actual::equals);
        return switch (c.operator()) {
            case IN, EQUALS -> contains;
            case NOT_IN, NOT_EQUALS -> !contains;
        };
    }
    private static String value(ConditionField field, ParticipantAttributes a) {
        return switch (field) {
            case ORG_UNIT -> a.orgUnitId() == null ? null : a.orgUnitId().toString();
            case POSITION -> a.positionCode();
            case GRADE -> a.gradeCode();
            case JOB -> a.jobCode();
            case EMPLOYMENT_TYPE -> a.employmentType();
            case EMPLOYEE -> a.employeeId().toString();
        };
    }
}
