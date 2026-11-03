package org.volunteers.scheduler.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.repository.*;
import org.volunteers.scheduler.service.OwnerIdentity;

@Component
@org.springframework.core.annotation.Order(100)
public class EventAdministratorBackfill implements ApplicationRunner {
    private final Events events;
    private final Volunteers volunteers;
    private final OwnerIdentity ownerIdentity;
    private final Assignments assignments;
    public EventAdministratorBackfill(Events events, Volunteers volunteers, OwnerIdentity ownerIdentity, Assignments assignments) {
        this.events = events; this.volunteers = volunteers; this.ownerIdentity = ownerIdentity; this.assignments = assignments;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        events.findAll().stream().map(Event::getId).sorted().forEach(id -> {
            var event = events.lockForVolunteerUpdate(id).orElseThrow();
            var members = volunteers.findByEventIdOrderByNameAscIdAsc(id);
            var ownerMembers = members.stream().filter(v -> ownerIdentity.matches(v.getPhone())).toList();
            var ownerIds = ownerMembers.stream().map(Volunteer::getId).collect(java.util.stream.Collectors.toSet());
            if (!ownerIds.isEmpty()) {
                assignments.deleteAll(assignments.findByShiftDepartmentEventIdOrderByIdAsc(id).stream()
                    .filter(a -> ownerIds.contains(a.getVolunteer().getId())).toList());
                assignments.flush();
                volunteers.deleteAll(ownerMembers);
                volunteers.flush();
            }
            for (var member : members) {
                if (ownerIds.contains(member.getId())) continue;
                member.getDepartmentRoles().replaceAll((department, role) -> {
                    if (role == Volunteer.Role.ADMIN) {
                        member.setEventAdministrator(true);
                        return Volunteer.Role.VOLUNTEER;
                    }
                    return role;
                });
            }
        });
    }
}
