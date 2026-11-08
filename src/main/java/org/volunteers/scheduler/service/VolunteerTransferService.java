package org.volunteers.scheduler.service;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.volunteers.scheduler.model.*;
import org.volunteers.scheduler.repository.*;

@Service
@Transactional(readOnly = true)
public class VolunteerTransferService {
    public enum Action { ADD, MOVE }
    public record Row(Volunteer volunteer, String status) {}
    public record Review(Event source, Event destination, Volunteer.Role role,
                         Action action, List<Row> rows) {}
    private final Events events;
    private final Volunteers volunteers;
    private final Assignments assignments;
    private final VolunteerTransferAudits audits;
    private final PhoneAccess access;
    public VolunteerTransferService(Events events, Volunteers volunteers,
            Assignments assignments, VolunteerTransferAudits audits, PhoneAccess access) {
        this.events = events; this.volunteers = volunteers;
        this.assignments = assignments; this.audits = audits; this.access = access;
    }
    public void authorize(long source, HttpSession session) {
        if (!access.canAdminister(session, source)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    public List<Event> destinations(long source, HttpSession session) {
        authorize(source, session);
        return events.findAllByOrderByStartDateDescIdDesc().stream()
            .filter(e -> e.getId() != source && access.canAdminister(session, e.getId())).toList();
    }
    public Review review(long source, long destination, List<Long> ids, Action action, HttpSession session) {
        authorize(source, session); authorize(destination, session);
        if (source == destination) throw new IllegalArgumentException("Choose a different destination event.");
        if (ids == null || ids.isEmpty() || ids.size() > 500) throw new IllegalArgumentException("Select between 1 and 500 volunteers.");
        if (action == null) throw new IllegalArgumentException("Choose a valid action.");
        var existing = volunteers.findByEventIdOrderByNameAscIdAsc(destination);
        var sourceAssignments = assignments.findByShiftDepartmentEventIdOrderByIdAsc(source);
        List<Row> rows = new ArrayList<>();
        Set<String> selectedPhones = new HashSet<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Volunteer v = volunteers.findById(id).orElseThrow(() -> new IllegalArgumentException("Volunteer no longer exists."));
            if (v.getEvent().getId() != source || v.isParticipationEnded()) throw new IllegalArgumentException("Select active volunteers from the source event.");
            String status = existing.stream().anyMatch(other -> samePerson(v, other)) ? "Already added" : "Ready";
            String phone = PhoneAccess.normalize(v.getPhone());
            if (!phone.isEmpty()) {
                if (existing.stream().anyMatch(other -> phone.equals(PhoneAccess.normalize(other.getPhone()))))
                    status = "Phone number already exists in the destination event";
                else if (!selectedPhones.add(phone))
                    status = "Phone number is shared by selected volunteers";
            }
            if (action == Action.MOVE && status.equals("Ready")) {
                if (sourceAssignments.stream().anyMatch(a -> a.getVolunteer().getId().equals(id)
                    && LocalDateTime.of(a.getShift().getDate(), a.getShift().getEndTime()).isAfter(LocalDateTime.now())))
                    status = "Resolve upcoming source-event assignments before moving";
                if (v.isEventAdministrator() && volunteers.findByEventIdOrderByNameAscIdAsc(source).stream()
                    .noneMatch(other -> !ids.contains(other.getId()) && other.isEventAdministrator() && !other.isParticipationEnded()))
                    status = SchedulerService.LAST_ADMIN;
            }
            rows.add(new Row(v, status));
        }
        return new Review(events.findById(source).orElseThrow(), events.findById(destination).orElseThrow(), Volunteer.Role.VOLUNTEER, action, rows);
    }
    private boolean samePerson(Volunteer a, Volunteer b) {
        String phone = PhoneAccess.normalize(a.getPhone());
        if (!phone.isEmpty() && phone.equals(PhoneAccess.normalize(b.getPhone()))) return true;
        return a.getEmail() != null && !a.getEmail().isBlank() && a.getEmail().equalsIgnoreCase(b.getEmail())
            || phone.isEmpty() && a.getName().strip().equalsIgnoreCase(b.getName().strip())
                && Objects.equals(a.getCongregation(), b.getCongregation());
    }
    @Transactional
    public void execute(long source, long destination, List<Long> ids, Action action, HttpSession session) {
        // Use the same stable event locks as imports and profile creation.
        events.lockForVolunteerUpdate(Math.min(source, destination)).orElseThrow();
        events.lockForVolunteerUpdate(Math.max(source, destination)).orElseThrow();
        Review review = review(source, destination, ids, action, session);
        if (review.rows().stream().anyMatch(r -> !Set.of("Ready", "Already added").contains(r.status())))
            throw new IllegalArgumentException("Resolve the issues shown in the review before completing the add/move.");
        for (Row row : review.rows()) {
            Volunteer v = row.volunteer();
            if (row.status().equals("Already added")) continue;
            Volunteer copy = new Volunteer(); copy.setEvent(review.destination());
            copy.setName(v.getName()); copy.setPhone(v.getPhone()); copy.setEmail(v.getEmail());
            copy.setCongregation(v.getCongregation()); copy.setPrivilege(v.getPrivilege()); copy.setNotes("");
            volunteers.save(copy);
            String previous = v.getDepartmentRoles().entrySet().stream()
                .map(e -> e.getKey().getName() + ": " + e.getValue().name()).toList().toString();
            audits.save(new VolunteerTransferAudit((String) session.getAttribute(PhoneAccess.SESSION), v,
                destination, action.name(), "Source " + previous + "; source administrator=" + v.isEventAdministrator()
                    + "; destination No Department Yet: VOLUNTEER"));
            if (action == Action.MOVE) {
                v.setParticipationEnded(true); v.setEventAdministrator(false); v.getDepartmentRoles().clear();
                volunteers.save(v);
            }
        }
    }
}
