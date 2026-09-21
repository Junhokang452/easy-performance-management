package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.AdjustmentResponse;
import com.easyperformance.program.ProgramDtos.CalculationResponse;
import com.easyperformance.program.ProgramDtos.EmployeeFeedbackDetail;
import com.easyperformance.program.ProgramDtos.FeedbackResponse;
import com.easyperformance.program.ProgramDtos.GradeCount;
import com.easyperformance.program.ProgramDtos.GradeMatrixCell;
import com.easyperformance.program.ProgramDtos.GradeMatrixResponse;
import com.easyperformance.program.ProgramDtos.ItemResultRow;
import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.PersonalHistoryPoint;
import com.easyperformance.program.ProgramDtos.PersonalReportResponse;
import com.easyperformance.program.ProgramDtos.PivotCell;
import com.easyperformance.program.ProgramDtos.PivotRequest;
import com.easyperformance.program.ProgramDtos.PivotResponse;
import com.easyperformance.program.ProgramDtos.ResultRow;
import com.easyperformance.program.ProgramDtos.ResultSummaryResponse;
import com.easyperformance.program.ProgramDtos.ReviewItemAnswerResponse;
import com.easyperformance.program.ProgramDtos.ReviewSubmissionResponse;
import com.easyperformance.program.ProgramDtos.ReviewerResultRow;
import com.easyperformance.program.ProgramDtos.ReviewerTendencyResponse;
import com.easyperformance.program.ProgramTypes.AdjustmentStatus;
import com.easyperformance.program.ProgramTypes.CalculationStatus;
import com.easyperformance.program.ProgramTypes.FeedbackStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.SubmissionStatus;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Deterministic analytics over finalized and explicitly published evaluation results. */
@Service
public class ProgramAnalyticsService {
    private static final Set<String> PIVOT_AXES = Set.of("department", "grade", "position", "job", "evaluationYear");
    private static final Set<String> POSITIVE = Set.of("좋음", "우수", "성장", "달성", "개선", "협업", "강점", "완료",
        "good", "great", "excellent", "growth", "achieved", "improved", "collaboration", "strength");
    private static final Set<String> NEGATIVE = Set.of("부족", "미흡", "지연", "실패", "위험", "문제", "보완", "우려",
        "poor", "weak", "delay", "failed", "risk", "problem", "concern", "missing");
    private static final Set<String> STOP_WORDS = Set.of("그리고", "그러나", "대한", "위한", "있는", "합니다", "했습니다",
        "the", "and", "for", "with", "this", "that", "from", "have", "has", "was", "were");

    private final ProgramAccess access;
    private final EvaluationProgramRepository programs;
    private final ProgramParticipantRepository participants;
    private final ProgramReviewSubmissionRepository submissions;
    private final ProgramCalculationRepository calculations;
    private final ProgramAdjustmentRepository adjustments;
    private final ProgramFeedbackRepository feedback;
    private final RmEmployeeRepository employees;
    private final ProgramJson json;

    public ProgramAnalyticsService(ProgramAccess access, EvaluationProgramRepository programs,
        ProgramParticipantRepository participants, ProgramReviewSubmissionRepository submissions,
        ProgramCalculationRepository calculations, ProgramAdjustmentRepository adjustments,
        ProgramFeedbackRepository feedback, RmEmployeeRepository employees, ProgramJson json) {
        this.access = access; this.programs = programs; this.participants = participants; this.submissions = submissions;
        this.calculations = calculations; this.adjustments = adjustments; this.feedback = feedback;
        this.employees = employees; this.json = json;
    }

    @Transactional(readOnly = true)
    public ResultSummaryResponse resultSummary(Actor actor, UUID programId) {
        EvaluationProgram program = finalized(actor, programId);
        List<FinalResult> results = results(actor.tenantId(), program);
        List<GradeCount> grades = results.stream().collect(Collectors.groupingBy(FinalResult::grade, LinkedHashMap::new, Collectors.counting()))
            .entrySet().stream().map(e -> new GradeCount(e.getKey(), e.getValue())).toList();
        List<ResultRow> rows = results.stream().map(r -> new ResultRow(r.participant().getId(), r.attributes(), r.score(),
            r.grade(), true, visibleFeedbackStatus(actor.tenantId(), r.participant().getId()))).toList();
        return new ResultSummaryResponse(programId, results.size(), grades, rows);
    }

    @Transactional(readOnly = true)
    public List<ItemResultRow> itemResults(Actor actor, UUID programId) {
        EvaluationProgram program = finalized(actor, programId);
        Map<UUID, FinalResult> resultByParticipant = results(actor.tenantId(), program).stream()
            .collect(Collectors.toMap(r -> r.participant().getId(), Function.identity()));
        return submissions.findAllByTenantIdAndProgramIdAndStatus(actor.tenantId(), programId, SubmissionStatus.COMPLETED).stream()
            .filter(s -> resultByParticipant.containsKey(s.getParticipantId())).flatMap(s -> {
                FinalResult result = resultByParticipant.get(s.getParticipantId());
                return json.answers(s.getAnswersJson()).stream().map(answer -> new ItemResultRow(s.getParticipantId(),
                    result.attributes(), answer.itemId(), answer.title(), s.getRound(), answer.weightPercent(),
                    answer.numericScore(), answer.scaleCode(), result.grade()));
            }).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewerResultRow> reviewerResults(Actor actor, UUID programId) {
        EvaluationProgram program = finalized(actor, programId);
        Map<UUID, FinalResult> resultByParticipant = results(actor.tenantId(), program).stream()
            .collect(Collectors.toMap(r -> r.participant().getId(), Function.identity()));
        return submissions.findAllByTenantIdAndProgramIdAndStatus(actor.tenantId(), programId, SubmissionStatus.COMPLETED).stream()
            .filter(s -> s.getRole() == ProgramTypes.ReviewerRole.REVIEWER)
            .filter(s -> resultByParticipant.containsKey(s.getParticipantId())).map(s -> {
                FinalResult result = resultByParticipant.get(s.getParticipantId());
                return new ReviewerResultRow(s.getActorEmployeeId(), employeeName(actor.tenantId(), s.getActorEmployeeId()),
                    s.getParticipantId(), result.attributes(), s.getRound(), submissionScore(s), result.grade(), s.getOverallOpinion());
            }).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeFeedbackDetail employeeFeedback(Actor actor, UUID participantId) {
        access.requireOperator(actor);
        ProgramParticipant participant = participants.findByIdAndTenantId(participantId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND));
        EvaluationProgram program = finalized(actor, participant.getProgramId());
        if (!participant.isResultPublished()) throw new ApiException(ProgramErrorCode.RESULT_NOT_PUBLISHED);
        ProgramCalculation calculation = latestFinal(actor.tenantId(), participantId);
        ProgramAdjustment adjustment = adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(actor.tenantId(), participantId)
            .filter(a -> a.getStatus() == AdjustmentStatus.COMPLETED && a.getCalculationId().equals(calculation.getId())).orElse(null);
        ProgramFeedback feed = feedback.findByTenantIdAndParticipantId(actor.tenantId(), participantId)
            .filter(f -> f.getStatus() != FeedbackStatus.DRAFT).orElse(null);
        List<ReviewSubmissionResponse> completed = submissions.findAllByTenantIdAndParticipantIdOrderByRoundAsc(actor.tenantId(), participantId)
            .stream().filter(s -> s.getStatus() == SubmissionStatus.COMPLETED).map(this::submissionResponse).toList();
        return new EmployeeFeedbackDetail(participantId, attributes(participant), calculationResponse(calculation),
            adjustment == null ? null : adjustmentResponse(adjustment), feed == null ? null : feedbackResponse(feed), completed);
    }

    @Transactional(readOnly = true)
    public GradeMatrixResponse gradeMatrix(Actor actor, UUID xProgramId, UUID yProgramId) {
        EvaluationProgram x = finalized(actor, xProgramId); EvaluationProgram y = finalized(actor, yProgramId);
        Map<UUID, FinalResult> yByEmployee = results(actor.tenantId(), y).stream()
            .collect(Collectors.toMap(r -> r.participant().getEmployeeId(), Function.identity(), (a, b) -> a));
        Map<String, List<ParticipantAttributes>> grouped = new LinkedHashMap<>();
        for (FinalResult left : results(actor.tenantId(), x)) {
            FinalResult right = yByEmployee.get(left.participant().getEmployeeId()); if (right == null) continue;
            grouped.computeIfAbsent(left.grade() + "\u0000" + right.grade(), ignored -> new ArrayList<>()).add(left.attributes());
        }
        List<GradeMatrixCell> cells = grouped.entrySet().stream().map(e -> {
            String[] key = e.getKey().split("\u0000", -1); return new GradeMatrixCell(key[0], key[1], e.getValue().size(), e.getValue());
        }).toList();
        return new GradeMatrixResponse(xProgramId, yProgramId, cells);
    }

    @Transactional(readOnly = true)
    public PivotResponse pivot(Actor actor, PivotRequest request) {
        access.requireOperator(actor);
        LinkedHashSet<String> axes = new LinkedHashSet<>(); axes.addAll(request.rowAxes()); axes.addAll(request.columnAxes());
        if (!PIVOT_AXES.containsAll(axes) || !axes.contains("grade")) {
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("allowedAxes", PIVOT_AXES, "requiredAxis", "grade"));
        }
        EvaluationProgram program = finalized(actor, request.programId());
        Map<Map<String, String>, List<FinalResult>> grouped = new LinkedHashMap<>();
        for (FinalResult result : results(actor.tenantId(), program)) {
            LinkedHashMap<String, String> dimensions = new LinkedHashMap<>();
            for (String axis : axes) dimensions.put(axis, dimension(axis, program, result));
            grouped.computeIfAbsent(Map.copyOf(dimensions), ignored -> new ArrayList<>()).add(result);
        }
        return new PivotResponse(grouped.entrySet().stream().map(e -> new PivotCell(e.getKey(), e.getKey().get("grade"),
            e.getValue().size(), e.getValue().stream().map(FinalResult::attributes).toList())).toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewerTendencyResponse> reviewerTendencies(Actor actor, UUID programId) {
        EvaluationProgram program = finalized(actor, programId);
        Set<UUID> published = results(actor.tenantId(), program).stream().map(r -> r.participant().getId()).collect(Collectors.toSet());
        Map<UUID, List<ProgramReviewSubmission>> byReviewer = submissions
            .findAllByTenantIdAndProgramIdAndStatus(actor.tenantId(), programId, SubmissionStatus.COMPLETED).stream()
            .filter(s -> s.getRole() == ProgramTypes.ReviewerRole.REVIEWER)
            .filter(s -> published.contains(s.getParticipantId())).collect(Collectors.groupingBy(ProgramReviewSubmission::getActorEmployeeId));
        Map<UUID, BigDecimal> means = byReviewer.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            e -> mean(e.getValue().stream().map(this::submissionScore).toList())));
        List<Map.Entry<UUID, BigDecimal>> ranked = means.entrySet().stream()
            .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).toList();
        Map<UUID, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) ranks.put(ranked.get(i).getKey(), i + 1);
        return byReviewer.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> tendency(actor.tenantId(), entry.getKey(),
            entry.getValue(), means.get(entry.getKey()), ranked.size() < 2 ? null : ranks.get(entry.getKey()))).toList();
    }

    @Transactional(readOnly = true)
    public PersonalReportResponse personalReport(Actor actor, UUID employeeId) {
        if (!access.operator(actor) && !Objects.equals(actor.employeeId(), employeeId)) {
            throw new ApiException(ProgramErrorCode.PROGRAM_FORBIDDEN);
        }
        List<EvaluationProgram> all = programs.findAllByTenantIdOrderByEvaluationYearDescCreatedAtDesc(actor.tenantId(), PageRequest.of(0, 10000)).getContent();
        Map<UUID, EvaluationProgram> finalized = all.stream().filter(p -> p.getStatus() == ProgramStatus.FINALIZED)
            .collect(Collectors.toMap(EvaluationProgram::getId, Function.identity()));
        List<PersonalHistoryPoint> history = participants.findAllByTenantIdAndEmployeeIdAndStatusOrderByCreatedAtDesc(
                actor.tenantId(), employeeId, ParticipantStatus.ACTIVE).stream()
            .filter(ProgramParticipant::isResultPublished).filter(p -> finalized.containsKey(p.getProgramId())).map(p -> {
                EvaluationProgram program = finalized.get(p.getProgramId()); FinalScore score = finalScore(actor.tenantId(), p.getId());
                boolean gradeOnly = !access.operator(actor) && json.configuration(program.getDefinitionJson()).publication().memberResultVisibility() == ProgramTypes.MemberResultVisibility.GRADE_ONLY;
                return new PersonalHistoryPoint(program.getEvaluationYear(), program.getId(), program.getName(), program.getKind(), gradeOnly ? null : score.score(), score.grade());
            }).sorted(Comparator.comparing(PersonalHistoryPoint::year).reversed()).toList();
        return new PersonalReportResponse(employeeId, history);
    }

    private ReviewerTendencyResponse tendency(UUID tenantId, UUID reviewerId, List<ProgramReviewSubmission> rows,
                                               BigDecimal meanScore, Integer rank) {
        List<BigDecimal> scores = rows.stream().map(this::submissionScore).toList();
        List<String> opinions = rows.stream().map(ProgramReviewSubmission::getOverallOpinion)
            .filter(value -> value != null && !value.isBlank()).toList();
        List<String> unavailable = new ArrayList<>();
        if (rank == null) unavailable.add("RANK_REQUIRES_MULTIPLE_REVIEWERS");
        BigDecimal std = scores.size() < 2 ? null : standardDeviation(scores, meanScore);
        if (std == null) unavailable.add("STANDARD_DEVIATION_REQUIRES_MULTIPLE_TARGETS");
        if (opinions.isEmpty()) unavailable.add("OPINION_DATA_UNAVAILABLE");
        BigDecimal specificity = opinions.isEmpty() ? null : specificity(opinions);
        BigDecimal positive = null, neutral = null, negative = null, averageLength = null;
        List<String> keywords = List.of();
        if (!opinions.isEmpty()) {
            long positiveCount = opinions.stream().filter(o -> sentiment(o) > 0).count();
            long negativeCount = opinions.stream().filter(o -> sentiment(o) < 0).count();
            long neutralCount = opinions.size() - positiveCount - negativeCount;
            positive = ratio(positiveCount, opinions.size()); neutral = ratio(neutralCount, opinions.size());
            negative = ratio(negativeCount, opinions.size());
            averageLength = BigDecimal.valueOf(opinions.stream().mapToInt(String::length).average().orElse(0)).setScale(2, RoundingMode.HALF_UP);
            keywords = keywords(opinions);
        }
        long targets = rows.stream().map(ProgramReviewSubmission::getParticipantId).distinct().count();
        return new ReviewerTendencyResponse(reviewerId, employeeName(tenantId, reviewerId), targets, meanScore, rank, std,
            specificity, positive, neutral, negative, averageLength, keywords, List.copyOf(unavailable));
    }

    private List<FinalResult> results(UUID tenantId, EvaluationProgram program) {
        return participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, program.getId()).stream()
            .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE && p.isResultPublished()).map(p -> {
                FinalScore score = finalScore(tenantId, p.getId()); return new FinalResult(p, attributes(p), score.score(), score.grade());
            }).toList();
    }

    private FinalScore finalScore(UUID tenantId, UUID participantId) {
        ProgramCalculation calculation = latestFinal(tenantId, participantId);
        return adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participantId)
            .filter(a -> a.getStatus() == AdjustmentStatus.COMPLETED && a.getCalculationId().equals(calculation.getId()))
            .map(a -> new FinalScore(a.getAdjustedScore(), a.getAdjustedGrade()))
            .orElseGet(() -> new FinalScore(calculation.getAdjustedScore(), calculation.getCalculatedGrade()));
    }

    private ProgramCalculation latestFinal(UUID tenantId, UUID participantId) {
        return calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participantId).stream()
            .filter(c -> c.getStatus() == CalculationStatus.FINAL).findFirst()
            .orElseThrow(() -> new ApiException(ProgramErrorCode.RESULT_NOT_PUBLISHED));
    }

    private EvaluationProgram finalized(Actor actor, UUID programId) {
        access.requireOperator(actor);
        EvaluationProgram program = programs.findByIdAndTenantId(programId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        if (program.getStatus() != ProgramStatus.FINALIZED) throw new ApiException(ProgramErrorCode.RESULT_NOT_PUBLISHED);
        return program;
    }

    private ParticipantAttributes attributes(ProgramParticipant participant) { return json.participantAttributes(participant.getAttributesJson()); }
    private String visibleFeedbackStatus(UUID tenantId, UUID participantId) {
        return feedback.findByTenantIdAndParticipantId(tenantId, participantId).filter(f -> f.getStatus() != FeedbackStatus.DRAFT)
            .map(f -> f.getStatus().name()).orElse(null);
    }
    private String employeeName(UUID tenantId, UUID id) {
        return employees.findByIdAndTenantId(id, tenantId).map(e -> e.getName()).orElse("Unknown");
    }
    private BigDecimal submissionScore(ProgramReviewSubmission submission) {
        List<ReviewItemAnswerResponse> answers = json.answers(submission.getAnswersJson());
        BigDecimal denominator = answers.stream().map(ReviewItemAnswerResponse::weightPercent).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (denominator.signum() == 0) return BigDecimal.ZERO;
        return answers.stream().filter(a -> a.numericScore() != null && a.weightPercent() != null)
            .map(a -> a.numericScore().multiply(a.weightPercent())).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(denominator, 4, RoundingMode.HALF_UP);
    }
    private static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }
    private static BigDecimal standardDeviation(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal variance = values.stream().map(v -> v.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(4, RoundingMode.HALF_UP);
    }
    private static BigDecimal specificity(List<String> opinions) {
        long concrete = opinions.stream().filter(value -> value.matches(".*(?:\\d|완료|달성|개선|실행|기한|목표|action|complete|achiev|improv|deadline|target).*" )).count();
        return ratio(concrete, opinions.size());
    }
    private static int sentiment(String opinion) {
        List<String> tokens = tokens(opinion); long positive = tokens.stream().filter(token -> lexiconMatch(token, POSITIVE)).count();
        long negative = tokens.stream().filter(token -> lexiconMatch(token, NEGATIVE)).count(); return Long.compare(positive, negative);
    }
    private static boolean lexiconMatch(String token, Set<String> lexicon) {
        return lexicon.stream().anyMatch(word -> token.equals(word) || token.startsWith(word));
    }
    private static List<String> keywords(List<String> opinions) {
        Map<String, Long> counts = opinions.stream().flatMap(o -> tokens(o).stream()).filter(t -> t.length() >= 2)
            .filter(t -> !STOP_WORDS.contains(t)).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
            .limit(5).map(Map.Entry::getKey).toList();
    }
    private static List<String> tokens(String value) {
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            .filter(token -> !token.isBlank()).toList();
    }
    private static BigDecimal ratio(long count, long total) {
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
    private static String dimension(String axis, EvaluationProgram program, FinalResult result) {
        return switch (axis) {
            case "department" -> Objects.toString(result.attributes().orgUnitName(), "UNASSIGNED");
            case "grade" -> result.grade();
            case "position" -> Objects.toString(result.attributes().positionCode(), "UNASSIGNED");
            case "job" -> Objects.toString(result.attributes().jobCode(), "UNASSIGNED");
            case "evaluationYear" -> program.getEvaluationYear().toString();
            default -> throw new ApiException(ProgramErrorCode.PROGRAM_INVALID);
        };
    }

    private CalculationResponse calculationResponse(ProgramCalculation c) {
        return new CalculationResponse(c.getId(), c.getParticipantId(), c.getRevision(), json.contributions(c.getContributionsJson()),
            c.getRawScore(), c.getNormalizedScore(), c.getAdjustedScore(), c.getCalculatedGrade(), c.getStatus(), c.getFormula(),
            json.strings(c.getWarningsJson()), c.getCalculatedAt());
    }
    private static AdjustmentResponse adjustmentResponse(ProgramAdjustment a) {
        return new AdjustmentResponse(a.getId(), a.getParticipantId(), a.getRevision(), a.getCalculationId(), a.getBeforeScore(), a.getBeforeGrade(),
            a.getAdjustedScore(), a.getAdjustedGrade(), a.getReason(), a.getStatus(), a.getActorEmployeeId(), a.getCompletedAt(), a.getRowVersion());
    }
    private static FeedbackResponse feedbackResponse(ProgramFeedback f) {
        return new FeedbackResponse(f.getId(), f.getParticipantId(), f.getWriterEmployeeId(), f.getComment(), f.getStatus(),
            f.getAppealReason(), f.getResolution(), f.getResolutionComment(), f.getDeliveredAt(), f.getResolvedAt(), f.getRowVersion());
    }
    private ReviewSubmissionResponse submissionResponse(ProgramReviewSubmission s) {
        return new ReviewSubmissionResponse(s.getId(), s.getParticipantId(), s.getReviewerAssignmentId(), s.getRole(), s.getRound(),
            json.answers(s.getAnswersJson()), s.getOverallOpinion(), s.getStatus(), s.getCompletedAt(), s.getRowVersion());
    }
    private record FinalScore(BigDecimal score, String grade) {}
    private record FinalResult(ProgramParticipant participant, ParticipantAttributes attributes, BigDecimal score, String grade) {}
}
