package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.spreadsheet.SimpleXlsx;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ProgramAnalyticsExportService {
    private final ProgramAnalyticsService analytics;

    public ProgramAnalyticsExportService(ProgramAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public byte[] resultsXlsx(Actor actor, UUID programId) {
        ResultSummaryResponse result = analytics.resultSummary(actor, programId);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("participantId", "employeeNo", "employeeName", "department", "positionCode",
            "gradeCode", "jobCode", "score", "resultGrade", "feedbackStatus"));
        for (ResultRow row : result.rows()) {
            ParticipantAttributes e = row.employee();
            rows.add(List.of(text(row.participantId()), text(e.employeeNo()), text(e.name()), text(e.orgUnitName()),
                text(e.positionCode()), text(e.gradeCode()), text(e.jobCode()), text(row.score()), text(row.grade()),
                text(row.feedbackStatus())));
        }
        return SimpleXlsx.write("Evaluation Results", rows);
    }

    @Transactional(readOnly = true)
    public byte[] pivotXlsx(Actor actor, PivotRequest request) {
        PivotResponse result = analytics.pivot(actor, request);
        List<String> axes = axes(request);
        List<List<String>> rows = new ArrayList<>();
        List<String> header = new ArrayList<>(axes); header.add("count"); header.add("employeeNos"); rows.add(header);
        for (PivotCell cell : result.cells()) {
            List<String> row = new ArrayList<>();
            axes.forEach(axis -> row.add(text(cell.dimensions().get(axis))));
            row.add(Long.toString(cell.count()));
            row.add(cell.employees().stream().map(ParticipantAttributes::employeeNo).filter(Objects::nonNull)
                .reduce((a, b) -> a + "," + b).orElse(""));
            rows.add(row);
        }
        return SimpleXlsx.write("Pivot", rows);
    }

    @Transactional(readOnly = true)
    public byte[] pivotSvg(Actor actor, PivotRequest request) {
        PivotResponse result = analytics.pivot(actor, request);
        List<String> axes = axes(request);
        int rowHeight = 32, top = 48, width = 960, height = Math.max(120, top + result.cells().size() * rowHeight + 24);
        long max = result.cells().stream().mapToLong(PivotCell::count).max().orElse(1L);
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            .append(width).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">")
            .append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/><text x=\"24\" y=\"28\" font-family=\"sans-serif\" font-size=\"18\" font-weight=\"700\">Evaluation pivot</text>");
        for (int i = 0; i < result.cells().size(); i++) {
            PivotCell cell = result.cells().get(i); int y = top + i * rowHeight;
            String label = axes.stream().map(axis -> cell.dimensions().getOrDefault(axis, "")).reduce((a, b) -> a + " / " + b).orElse("");
            int bar = (int) Math.round(620d * cell.count() / max);
            svg.append("<text x=\"24\" y=\"").append(y + 18).append("\" font-family=\"sans-serif\" font-size=\"13\">").append(xml(label)).append("</text>")
                .append("<rect x=\"300\" y=\"").append(y + 3).append("\" width=\"").append(bar).append("\" height=\"20\" rx=\"3\" fill=\"#2f6feb\"/>")
                .append("<text x=\"").append(308 + bar).append("\" y=\"").append(y + 18).append("\" font-family=\"sans-serif\" font-size=\"13\">").append(cell.count()).append("</text>");
        }
        return svg.append("</svg>").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> axes(PivotRequest request) {
        LinkedHashSet<String> axes = new LinkedHashSet<>(); axes.addAll(request.rowAxes()); axes.addAll(request.columnAxes());
        return List.copyOf(axes);
    }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
