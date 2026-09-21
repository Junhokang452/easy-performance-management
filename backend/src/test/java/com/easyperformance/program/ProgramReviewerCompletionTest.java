package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.AssignmentStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.program.ProgramTypes.SubmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramReviewerCompletionTest {

    @Test
    void oneCompletedSubmissionCannotSelectAOneReviewerPlanWhenAnotherReviewerIsPending() {
        ProgramReviewerAssignment first = assignment(1, AssignmentStatus.COMPLETED);
        ProgramReviewerAssignment second = assignment(2, AssignmentStatus.IN_PROGRESS);
        ProgramReviewSubmission submission = submission(first, 1);

        assertThatThrownBy(() -> ProgramExecutionService.validateReviewerCompletion(
            List.of(first, second), List.of(submission)))
            .isInstanceOf(ProgramRuleViolation.class)
            .hasMessageContaining("completed");
    }

    @Test
    void submissionForARevokedOrReplacedAssignmentCannotEnterCalculation() {
        ProgramReviewerAssignment active = assignment(1, AssignmentStatus.COMPLETED);
        ProgramReviewerAssignment revoked = assignment(1, AssignmentStatus.REVOKED);

        assertThatThrownBy(() -> ProgramExecutionService.validateReviewerCompletion(
            List.of(active), List.of(submission(revoked, 1))))
            .isInstanceOf(ProgramRuleViolation.class)
            .hasMessageContaining("all active reviewer assignments");
    }

    private ProgramReviewerAssignment assignment(int round, AssignmentStatus status) {
        ProgramReviewerAssignment assignment = new ProgramReviewerAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setRole(ReviewerRole.REVIEWER);
        assignment.setRound(round);
        assignment.setStatus(status);
        return assignment;
    }

    private ProgramReviewSubmission submission(ProgramReviewerAssignment assignment, int round) {
        ProgramReviewSubmission submission = new ProgramReviewSubmission();
        submission.setReviewerAssignmentId(assignment.getId());
        submission.setRole(ReviewerRole.REVIEWER);
        submission.setRound(round);
        submission.setStatus(SubmissionStatus.COMPLETED);
        return submission;
    }
}
