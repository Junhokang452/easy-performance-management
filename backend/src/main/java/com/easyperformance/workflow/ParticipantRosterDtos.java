package com.easyperformance.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ParticipantRosterDtos {
    private ParticipantRosterDtos() {}

    public record ParticipantReplaceRequest(@NotNull @Valid List<ParticipantInput> participants) {}
    public record ParticipantInput(@NotNull UUID employeeId, @NotNull UUID managerEmployeeId) {}
    public record EmployeeSummary(UUID id, String employeeNo, String name, UUID orgUnitId,
                                  String orgUnitName, String status, boolean hasUserBinding) {}
    public record ParticipantResponse(UUID id, UUID cycleId, EmployeeSummary employee,
                                      ParticipantStatus status, EmployeeSummary manager,
                                      UUID reviewId, String reviewStatus) {}
    public record ParticipantRosterResponse(UUID cycleId, long activeCount, long excludedCount,
                                            long missingManagerCount, List<ParticipantResponse> items) {}
}
