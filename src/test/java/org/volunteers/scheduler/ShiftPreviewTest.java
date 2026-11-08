package org.volunteers.scheduler;

import org.junit.jupiter.api.Test;
import org.volunteers.scheduler.model.Assignment;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.SchedulerService.ShiftRow;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShiftPreviewTest {
    private List<Assignment> team(int size) {
        return LongStream.rangeClosed(1, size).mapToObj(id -> {
            Volunteer volunteer = mock(Volunteer.class);
            when(volunteer.getId()).thenReturn(id);
            Assignment assignment = new Assignment();
            assignment.setVolunteer(volunteer);
            return assignment;
        }).toList();
    }
    @Test void smallTeamsShowEveryone() {
        for (int size = 0; size <= 6; size++) {
            var team = team(size);
            assertThat(new ShiftRow(null, team).previewAssignments(Set.of())).containsExactlyElementsOf(team);
        }
    }
    @Test void largeTeamsShowFiveAndKeepLoggedInVolunteerVisible() {
        var team = team(22);
        var row = new ShiftRow(null, team);
        assertThat(row.previewAssignments(Set.of())).containsExactlyElementsOf(team.subList(0, 5));
        assertThat(row.previewAssignments(Set.of(22L)))
                .containsExactly(team.get(0), team.get(1), team.get(2), team.get(3), team.get(21));
        assertThat(row.previewAssignments(Set.of(1L))).containsExactlyElementsOf(team.subList(0, 5));
        assertThat(row.assignments()).hasSize(22);
    }
}
