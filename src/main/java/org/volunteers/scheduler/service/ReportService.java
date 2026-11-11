package org.volunteers.scheduler.service;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.volunteers.scheduler.model.Assignment;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.model.Volunteer;

@org.springframework.stereotype.Service
public class ReportService {
    private final SchedulerQueries service;
    private final BackupService backups;
    public ReportService(SchedulerQueries service, BackupService backups) { this.service = service; this.backups = backups; }
    public ResponseEntity<byte[]> csv(long eventId, String type,
                               Long departmentId, LocalDate date,
                               String snapshot, HttpSession session) {
        if (BackupService.supports(type)) {
            var backup = backup(eventId, session);
            if (snapshot != null && !snapshot.equals(backup.token())) throw new IllegalArgumentException("Backup expired. Reload Reports.");
            return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + eventId + "-" + backup.token() + "-" + type + ".csv\"")
                    .body(backup.files().get(type));
        }
        var data = service.snapshot(eventId); String selected = reportType(type);
        List<SchedulerService.ShiftRow> rows = filtered(data, departmentId, date);
        StringBuilder out = new StringBuilder("\ufeff");
        if (selected.equals("volunteer")) {
            csvLine(out, "Volunteer", "Congregation", "Department", "Shift", "Date", "Start", "End");
            for (VolunteerReport v : volunteerReport(data, rows, departmentId != null || date != null)) {
                if (v.assignments().isEmpty()) csvLine(out, v.volunteer().getName(), v.volunteer().getCongregation(), "Unassigned", "", "", "", "");
                for (Assignment a : v.assignments()) csvLine(out, v.volunteer().getName(), v.volunteer().getCongregation(),
                        a.getShift().getDepartment().getName(), a.getShift().getName(), a.getShift().getDate(), a.getShift().getStartTime(), a.getShift().getEndTime());
            }
        } else {
            csvLine(out, "Department", "Shift", "Date", "Start", "End", "Required", "Assigned", "Missing", "Status", "Volunteers");
            for (var r : rows) csvLine(out, r.shift().getDepartment().getName(), r.shift().getName(), r.shift().getDate(), r.shift().getStartTime(),
                    r.shift().getEndTime(), r.shift().getRequiredCount(), r.assignments().size(), r.getMissing(), r.getStatus(),
                    String.join("; ", r.assignments().stream().map(a -> a.getVolunteer().getName()).toList()));
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + eventId + "-" + selected + ".csv\"")
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    public BackupService.Backup backup(long eventId, HttpSession session) {
        service.event(eventId);
        synchronized (session) {
            Object saved = session.getAttribute("csvBackup");
            if (saved instanceof BackupService.Backup backup && backup.eventId() == eventId) return backup;
            var backup = backups.capture(eventId);
            session.setAttribute("csvBackup", backup);
            return backup;
        }
    }

    public static List<VolunteerReport> volunteerReport(SchedulerService.Snapshot data, List<SchedulerService.ShiftRow> rows, boolean filtered) {
        Map<Long, List<Assignment>> byVolunteer = new HashMap<>();
        rows.forEach(row -> row.assignments().forEach(assignment ->
                byVolunteer.computeIfAbsent(assignment.getVolunteer().getId(), ignored -> new ArrayList<>()).add(assignment)));
        return data.volunteers().stream()
                .map(v -> new VolunteerReport(v, byVolunteer.getOrDefault(v.getId(), List.of())))
                .filter(v -> !filtered || !v.assignments().isEmpty()).toList();
    }

    public static String reportType(String value) {
        if (!Set.of("department", "volunteer", "coverage").contains(value) && !BackupService.supports(value)) throw new IllegalArgumentException("Unknown report type.");
        return value;
    }

    static void csvLine(StringBuilder out, Object... values) {
        StringJoiner line = new StringJoiner(",");
        for (Object value : values) {
            String text = Objects.toString(value, "");
            if (text.stripLeading().matches("(?s)^[=+@\\-\\t\\r\\n].*")) text = "'" + text;
            line.add("\"" + text.replace("\"", "\"\"") + "\"");
        }
        out.append(line).append("\r\n");
    }

    public static List<SchedulerService.ShiftRow> filtered(SchedulerService.Snapshot data, Long departmentId, LocalDate date) {
        if (departmentId != null && data.departments().stream().noneMatch(d -> d.getId().equals(departmentId)))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        return data.rows().stream().filter(r -> departmentId == null || r.shift().getDepartment().getId().equals(departmentId))
                .filter(r -> date == null || r.shift().getDate().equals(date)).toList();
    }

    public record VolunteerReport(Volunteer volunteer, List<Assignment> assignments) { }
}
