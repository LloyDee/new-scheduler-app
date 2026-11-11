package org.volunteers.scheduler.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.volunteers.scheduler.model.Assignment;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.model.Volunteer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    @Test
    void groupingPreservesVolunteerAndShiftOrderAndKeepsUnassignedVolunteers() {
        Volunteer first = volunteer(1L), second = volunteer(2L), unassigned = volunteer(3L);
        Assignment earlySecond = assignment(second), earlyFirst = assignment(first), lateFirst = assignment(first);
        var rows = List.of(new SchedulerService.ShiftRow(new Shift(), List.of(earlySecond, earlyFirst)),
                new SchedulerService.ShiftRow(new Shift(), List.of(lateFirst)));
        var data = new SchedulerService.Snapshot(null, List.of(first, second, unassigned),
                List.of(), List.of(), List.of(earlySecond, earlyFirst, lateFirst));

        var report = ReportService.volunteerReport(data, rows, false);
        assertThat(report).extracting(ReportService.VolunteerReport::volunteer)
                .containsExactly(first, second, unassigned);
        assertThat(report.get(0).assignments()).containsExactly(earlyFirst, lateFirst);
        assertThat(report.get(1).assignments()).containsExactly(earlySecond);
        assertThat(report.get(2).assignments()).isEmpty();

        var filtered = ReportService.volunteerReport(data, rows.subList(1, 2), true);
        assertThat(filtered).extracting(ReportService.VolunteerReport::volunteer).containsExactly(first);
        assertThat(filtered.getFirst().assignments()).containsExactly(lateFirst);
        assertThat(ReportService.volunteerReport(data, List.of(), true)).isEmpty();
    }

    @Test
    void csvEscapesQuotesNewlinesAndSpreadsheetFormulas() {
        StringBuilder csv = new StringBuilder();
        ReportService.csvLine(csv, "=SUM(A1)", "He said \"hello\"", "two\nlines", null);
        assertThat(csv.toString()).isEqualTo("\"'=SUM(A1)\",\"He said \"\"hello\"\"\",\"two\nlines\",\"\"\r\n");
    }

    private static Volunteer volunteer(long id) {
        Volunteer volunteer = mock(Volunteer.class);
        when(volunteer.getId()).thenReturn(id);
        return volunteer;
    }

    private static Assignment assignment(Volunteer volunteer) {
        Assignment assignment = new Assignment();
        assignment.setVolunteer(volunteer);
        return assignment;
    }
}
