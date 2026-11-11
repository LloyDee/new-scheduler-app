package org.volunteers.scheduler.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.volunteers.scheduler.model.Assignment;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.repository.Assignments;
import org.volunteers.scheduler.repository.Departments;
import org.volunteers.scheduler.repository.Events;
import org.volunteers.scheduler.repository.Volunteers;

import static org.volunteers.scheduler.service.SchedulerService.*;

@Service
@Transactional(readOnly = true)
public class VolunteerService {
    private final Events events;
    private final Volunteers volunteers;
    private final Departments departments;
    private final Assignments assignments;
    private final OwnerIdentity ownerIdentity;
    private final SchedulerQueries schedulerQueries;

    public VolunteerService(Events events, Volunteers volunteers, Departments departments, Assignments assignments, OwnerIdentity ownerIdentity, SchedulerQueries schedulerQueries) {
        this.events = events;
        this.volunteers = volunteers;
        this.departments = departments;
        this.assignments = assignments;
        this.ownerIdentity = ownerIdentity;
        this.schedulerQueries = schedulerQueries;
    }

    public VolunteerPage volunteerPage(long eventId, Long departmentId, String search, int page, List<Long> details) {
        return volunteerPage(eventId, departmentId, search, page, details, true);
    }
    public VolunteerPage volunteerPage(long eventId, Long departmentId, String search, int page, List<Long> details, boolean histories) {
        Event event = schedulerQueries.event(eventId);
        var eventDepartments = departments.findByEventIdOrderByNameAscIdAsc(eventId);
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), 25);
        var ids = volunteers.findPageIds(eventId, departmentId, search.strip().toLowerCase(Locale.ROOT), pageable);
        Set<Long> needed = new LinkedHashSet<>(ids.getContent());
        needed.addAll(details);
        var records = needed.isEmpty() ? List.<Volunteer>of() : volunteers.findByIdInOrderByNameAscIdAsc(needed);
        for (Long id : details) {
            if (records.stream().noneMatch(v -> v.getId().equals(id) && v.getEvent().getId().equals(eventId))) throw notFound();
        }
        var eventAssignments = !histories || needed.isEmpty() ? List.<Assignment>of() : assignments.findForVolunteerShifts(needed);
        var eventShifts = eventAssignments.stream().map(Assignment::getShift).distinct()
            .sorted(Comparator.comparing(Shift::getDate).thenComparing(Shift::getStartTime).thenComparing(Shift::getId)).toList();
        var visible = records.stream().filter(v -> ids.getContent().contains(v.getId())).toList();
        return new VolunteerPage(new Snapshot(event, records, eventDepartments, eventShifts, eventAssignments),
            new org.springframework.data.domain.PageImpl<>(visible, pageable, ids.getTotalElements()));
    }

    @Transactional
    public Volunteer saveVolunteer(long eventId, Long id, Map<String, String> input) {
        return saveVolunteer(eventId, id, input, true);
    }

    @Transactional
    public Volunteer saveVolunteer(long eventId, Long id, Map<String, String> input, boolean mayAdminister) {
        Event e = events.lockForVolunteerUpdate(eventId).orElseThrow(SchedulerService::notFound);
        List<Volunteer> existing = volunteers.findByEventIdOrderByNameAscIdAsc(eventId);
        Volunteer current = id == null ? null : existing.stream().filter(v -> v.getId().equals(id))
            .findFirst().orElseThrow(SchedulerService::notFound);
        if (!mayAdminister && ((current != null && current.isEventAdministrator())
                || Boolean.parseBoolean(input.get("eventAdministrator"))))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String name = required(input, "name", 160), phone = required(input, "phone", 60);
        ownerIdentity.requireVolunteerPhone(phone);
        require(phone.matches("[0-9() .-]+"), "Enter a 10-digit phone number.");
        String phoneDigits = phone.replaceAll("[^0-9]", "");
        require(phoneDigits.length() == 10, "Enter a 10-digit phone number.");
        phone = "(" + phoneDigits.substring(0, 3) + ")" + phoneDigits.substring(3, 6) + "-" + phoneDigits.substring(6);
        String email = optional(input, "email", 254);
        require(email.isEmpty() || email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Enter a valid email address.");
        String congregation = required(input, "congregation", 160), notes = optional(input, "notes", 2000);
        String privilegeValue = required(input, "privilege", 40);
        Volunteer.Privilege privilege = null;
        if (!privilegeValue.isEmpty()) {
            try { privilege = Volunteer.Privilege.valueOf(privilegeValue); }
            catch (IllegalArgumentException ex) { throw invalid("Choose a valid privilege."); }
        }
        Map<Department, Volunteer.Role> departmentRoles = new LinkedHashMap<>();
        for (var entry : input.entrySet()) {
            if (!entry.getKey().startsWith("departmentRole_")) continue;
            if (entry.getValue().isBlank()) continue;
            long departmentId = number(Map.of("departmentId", entry.getKey().substring("departmentRole_".length())), "departmentId");
            Department selected = schedulerQueries.department(eventId, departmentId);
            Volunteer.Role role;
            try { role = Volunteer.Role.valueOf(entry.getValue()); }
            catch (IllegalArgumentException ex) { throw invalid("Choose a valid role for " + selected.getName() + "."); }
            departmentRoles.put(selected, role);
        }
        require(departmentRoles.values().stream().noneMatch(role -> role == Volunteer.Role.ADMIN), "Use the event administrator setting instead of department Admin.");
        boolean administrator = Boolean.parseBoolean(input.getOrDefault("eventAdministrator", "false"));
        if (current != null && current.isEventAdministrator() && !administrator)
            require(existing.stream().anyMatch(other -> !Objects.equals(other.getId(), id) && other.isEventAdministrator()), LAST_ADMIN);
        boolean noDepartment = "none".equals(input.get("departmentChoice"));
        require(administrator || id == null || !noDepartment, "Choose a department and its role.");
        require(administrator || !departmentRoles.isEmpty() || noDepartment,
                id == null ? "Choose a department and its role, or No Department Yet." : "Choose a department and its role.");
        require(!noDepartment || departmentRoles.isEmpty(), "No Department Yet cannot be combined with department roles.");
        for (var membership : departmentRoles.entrySet()) {
            Volunteer.Role role = membership.getValue();
            int limit = role == Volunteer.Role.OVERSEER ? 1 : role == Volunteer.Role.ASSISTANT_OVERSEER ? 2 : Integer.MAX_VALUE;
            long count = existing.stream().filter(other -> !Objects.equals(other.getId(), id))
                    .filter(other -> other.getDepartmentRoles().entrySet().stream().anyMatch(entry ->
                            entry.getKey().getId().equals(membership.getKey().getId()) && entry.getValue() == role)).count();
            require(count < limit, membership.getKey().getName() + " allows at most " + limit + " "
                    + role.getLabel() + (limit == 1 ? "." : "s."));
        }
        Volunteer v = current == null ? new Volunteer() : current;
        v.setEvent(e); v.setName(name); v.setPhone(phone); v.setEmail(email); v.setCongregation(congregation); v.setNotes(notes);
        v.setEventAdministrator(administrator);
        v.setPrivilege(privilege);
        v.getDepartmentRoles().clear();
        v.getDepartmentRoles().putAll(departmentRoles);
        return volunteers.save(v);
    }

    @Transactional
    public void deleteVolunteer(long eventId, long id) {
        deleteVolunteer(eventId, id, true);
    }

    @Transactional
    public void deleteVolunteer(long eventId, long id, boolean mayAdminister) {
        events.lockForVolunteerUpdate(eventId).orElseThrow(SchedulerService::notFound);
        Volunteer v = schedulerQueries.volunteer(eventId, id);
        if (v.isEventAdministrator() && !mayAdminister) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        require(!v.isEventAdministrator() || volunteers.findByEventIdOrderByNameAscIdAsc(eventId).stream()
            .anyMatch(other -> !other.getId().equals(id) && other.isEventAdministrator()), LAST_ADMIN);
        assignments.deleteAll(assignments.findByShiftDepartmentEventIdOrderByIdAsc(eventId).stream()
                .filter(a -> a.getVolunteer().getId().equals(id)).toList());
        assignments.flush(); volunteers.delete(v);
    }
}
