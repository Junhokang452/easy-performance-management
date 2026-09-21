package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.AchievementLevelInput;
import com.easyperformance.program.ProgramDtos.ProgramConfiguration;
import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.ReviewItemAnswerResponse;
import com.easyperformance.program.ProgramDtos.ScoreContribution;
import com.easyperformance.program.ProgramDtos.TaskEvidenceSnapshot;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyResponse;
import com.easyperformance.program.ProgramKpiDtos.KpiLinkPreviewRow;
import com.easyperformance.program.ProgramKpiDtos.KpiSourceSnapshot;
import com.easyperformance.program.ProgramReminderDtos.ReminderQueueResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProgramJson {
    private final ObjectMapper mapper;
    public ProgramJson(ObjectMapper mapper) { this.mapper = mapper; }

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("program JSON serialization failed", e); }
    }
    public ProgramConfiguration configuration(String value) { return read(value, ProgramConfiguration.class); }
    public ParticipantAttributes participantAttributes(String value) { return read(value, ParticipantAttributes.class); }
    public List<AchievementLevelInput> achievementLevels(String value) { return readList(value, new TypeReference<>() {}); }
    public List<TaskEvidenceSnapshot> taskEvidence(String value) { return readList(value, new TypeReference<>() {}); }
    public List<ReviewItemAnswerResponse> answers(String value) { return readList(value, new TypeReference<>() {}); }
    public List<ScoreContribution> contributions(String value) { return readList(value, new TypeReference<>() {}); }
    public ReviewerLineApplyResponse reviewerLineApplyResponse(String value) {
        return read(value, ReviewerLineApplyResponse.class);
    }
    public KpiLinkPreviewRow kpiLinkPreviewRow(String value) { return read(value, KpiLinkPreviewRow.class); }
    public KpiSourceSnapshot kpiSourceSnapshot(String value) { return read(value, KpiSourceSnapshot.class); }
    public ReminderQueueResponse reminderQueueResponse(String value) { return read(value, ReminderQueueResponse.class); }
    public List<String> strings(String value) { return readList(value, new TypeReference<>() {}); }

    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("stored program JSON is invalid", e); }
    }
    private <T> List<T> readList(String value, TypeReference<List<T>> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("stored program JSON is invalid", e); }
    }
}
