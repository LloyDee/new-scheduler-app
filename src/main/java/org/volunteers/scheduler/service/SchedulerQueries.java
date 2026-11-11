package org.volunteers.scheduler.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.repository.Assignments;
import org.volunteers.scheduler.repository.Departments;
import org.volunteers.scheduler.repository.Events;
import org.volunteers.scheduler.repository.Shifts;
import org.volunteers.scheduler.repository.Volunteers;

import static org.volunteers.scheduler.service.SchedulerService.*;

@Service
@Transactional(readOnly = true)
public class SchedulerQueries {
    private final Events events;
    private final Volunteers volunteers;
    private final Departments departments;
    private final Shifts shifts;
    private final Assignments assignments;

    public SchedulerQueries(Events events, Volunteers volunteers, Departments departments, Shifts shifts, Assignments assignments) {
        this.events = events;
        this.volunteers = volunteers;
        this.departments = departments;
        this.shifts = shifts;
        this.assignments = assignments;
    }

    public Event event(long id) { return events.findById(id).orElseThrow(SchedulerService::notFound); }

    public Volunteer volunteer(long eventId, long id) {
        Volunteer v = volunteers.findById(id).orElseThrow(SchedulerService::notFound);
        sameEvent(eventId, v.getEvent().getId());
        require(!v.isParticipationEnded(), "This volunteer's participation has ended."); return v;
    }

    public Department department(long eventId, long id) {
        Department d = departments.findById(id).orElseThrow(SchedulerService::notFound);
        sameEvent(eventId, d.getEvent().getId()); return d;
    }

    public Shift shift(long eventId, long id) {
        Shift s = shifts.findById(id).orElseThrow(SchedulerService::notFound);
        sameEvent(eventId, s.getDepartment().getEvent().getId()); return s;
    }

    public Snapshot snapshot(long id) {
        Event event = event(id);
        // Resolve map keys before fetching volunteer roles to avoid individual department loads.
        var eventDepartments = departments.findByEventIdOrderByNameAscIdAsc(id);
        return new Snapshot(event, volunteers.findByEventIdOrderByNameAscIdAsc(id).stream().filter(v -> !v.isParticipationEnded()).toList(), eventDepartments,
                shifts.findByDepartmentEventIdOrderByDateAscStartTimeAscIdAsc(id),
                assignments.findByShiftDepartmentEventIdOrderByIdAsc(id));
    }
    public record SchedulePage(Snapshot snapshot, org.springframework.data.domain.Page<ShiftRow> page,
        java.util.List<java.time.LocalDate> dates, java.util.Map<Long, java.util.List<Volunteer>> options) { }

    private java.util.List<org.volunteers.scheduler.model.Assignment> pageAssignments(java.util.List<Shift> records) {
        return records.isEmpty() ? java.util.List.of() : assignments.findByShiftIdInOrderByIdAsc(records.stream().map(Shift::getId).toList());
    }

    public SchedulePage schedulePage(long eventId, Long departmentId, java.time.LocalDate date, String view,
            java.util.Set<Long> ownIds, int page, Long edit) {
        return schedulePage(eventId, departmentId, date, view, ownIds, page, edit, true);
    }
    public SchedulePage schedulePage(long eventId, Long departmentId, java.time.LocalDate date, String view,
            java.util.Set<Long> ownIds, int page, Long edit, boolean optionsRequested) {
        var event = event(eventId);
        var depts = departments.findByEventIdOrderByNameAscIdAsc(eventId);
        if (departmentId != null && depts.stream().noneMatch(d -> d.getId().equals(departmentId))) throw notFound();
        var result = shifts.findPage(eventId, departmentId, date, view,
            ownIds.isEmpty() ? java.util.Set.of(-1L) : ownIds, org.springframework.data.domain.PageRequest.of(Math.max(0, page), 25));
        var records = new java.util.ArrayList<>(result.getContent());
        if (edit != null && records.stream().noneMatch(s -> s.getId().equals(edit))) records.add(shift(eventId, edit));
        var assigned = pageAssignments(records);
        var eligible = !optionsRequested || records.isEmpty() ? java.util.List.<Volunteers.Eligible>of() :
            volunteers.findAvailableForShifts(eventId, records.stream().map(Shift::getId).toList());
        var ids = new java.util.HashSet<Long>();
        eligible.forEach(e -> ids.add(e.getVolunteerId()));
        if (optionsRequested) assigned.stream().filter(a -> !a.getVolunteer().isParticipationEnded()).forEach(a -> ids.add(a.getVolunteer().getId()));
        var people = ids.isEmpty() ? java.util.List.<Volunteer>of() : volunteers.findByIdInOrderByNameAscIdAsc(ids);
        var options = new java.util.HashMap<Long, java.util.List<Volunteer>>();
        for (var s : records) {
            var allowed = new java.util.HashSet<Long>();
            eligible.stream().filter(e -> e.getShiftId().equals(s.getId())).forEach(e -> allowed.add(e.getVolunteerId()));
            assigned.stream().filter(a -> a.getShift().getId().equals(s.getId())).forEach(a -> allowed.add(a.getVolunteer().getId()));
            options.put(s.getId(), people.stream().filter(v -> allowed.contains(v.getId())).toList());
        }
        var data = new Snapshot(event, people, depts, records, assigned);
        var rows = data.rows().stream().filter(r -> result.getContent().contains(r.shift())).toList();
        return new SchedulePage(data, new org.springframework.data.domain.PageImpl<>(rows, result.getPageable(), result.getTotalElements()),
            shifts.findDates(eventId), options);
    }

    public record DepartmentPage(Snapshot snapshot, org.springframework.data.domain.Page<Department> page) { }
    public DepartmentPage departmentPage(long eventId, int page, java.util.List<Long> details) {
        return departmentPage(eventId, page, details, true);
    }
    public DepartmentPage departmentPage(long eventId, int page, java.util.List<Long> details, boolean schedulesRequested) {
        var event = event(eventId);
        var result = departments.findByEventIdOrderByNameAscIdAsc(eventId, org.springframework.data.domain.PageRequest.of(Math.max(0, page), 25));
        var records = new java.util.ArrayList<>(result.getContent());
        for (var id : details) if (records.stream().noneMatch(d -> d.getId().equals(id))) records.add(department(eventId, id));
        var ids = records.stream().map(Department::getId).toList();
        var people = ids.isEmpty() ? java.util.List.<Volunteer>of() : volunteers.findMembers(eventId, ids);
        var schedule = !schedulesRequested || ids.isEmpty() ? java.util.List.<Shift>of() : shifts.findByDepartmentIdInOrderByDateAscStartTimeAscIdAsc(ids);
        return new DepartmentPage(new Snapshot(event, people, records, schedule, pageAssignments(schedule)), result);
    }

    public Snapshot volunteerDetails(long eventId, long id) {
        var v = volunteer(eventId, id);
        var assigned = assignments.findByVolunteerIdOrderByIdAsc(id);
        var records = assigned.stream().map(org.volunteers.scheduler.model.Assignment::getShift).distinct()
            .sorted(java.util.Comparator.comparing(Shift::getDate).thenComparing(Shift::getStartTime).thenComparing(Shift::getId)).toList();
        return new Snapshot(v.getEvent(), java.util.List.of(v), java.util.List.of(), records, assigned);
    }
    public Snapshot departmentDetails(long eventId, long id) {
        var d = department(eventId, id);
        var records = shifts.findByDepartmentIdInOrderByDateAscStartTimeAscIdAsc(java.util.List.of(id));
        return new Snapshot(d.getEvent(), java.util.List.of(), java.util.List.of(d), records, pageAssignments(records));
    }
    public Snapshot shiftDetails(long eventId, long id, boolean options) {
        var s = shift(eventId, id);
        var assigned = pageAssignments(java.util.List.of(s));
        var ids = new java.util.HashSet<Long>();
        if (options) {
            volunteers.findAvailableForShifts(eventId, java.util.List.of(id)).forEach(e -> ids.add(e.getVolunteerId()));
            assigned.stream().filter(a -> !a.getVolunteer().isParticipationEnded()).forEach(a -> ids.add(a.getVolunteer().getId()));
        }
        var people = ids.isEmpty() ? java.util.List.<Volunteer>of() : volunteers.findByIdInOrderByNameAscIdAsc(ids);
        return new Snapshot(s.getDepartment().getEvent(), people, java.util.List.of(), java.util.List.of(s), assigned);
    }

    public record Home(Snapshot snapshot, long volunteerCount, long departmentCount, long shiftCount, long missing,
                       java.util.List<Volunteer> contacts) { }
    public Home home(long eventId, java.util.Set<Long> ownIds, java.time.LocalDateTime now) {
        var event = event(eventId);
        var records = ownIds.isEmpty() ? java.util.List.<Shift>of() : shifts.findUpcoming(eventId, ownIds, now.toLocalDate(), now.toLocalTime());
        var contacts = records.isEmpty() ? java.util.List.<Volunteer>of() : volunteers.findMembers(eventId,
            java.util.List.of(records.getFirst().getDepartment().getId())).stream().filter(v ->
                v.getDepartmentRoles().get(records.getFirst().getDepartment()) == Volunteer.Role.OVERSEER).toList();
        return new Home(new Snapshot(event, java.util.List.of(), java.util.List.of(), records, pageAssignments(records)),
            volunteers.countByEventIdAndParticipationEndedFalse(eventId), departments.countByEventId(eventId),
            shifts.countByDepartmentEventId(eventId), shifts.countMissing(eventId), contacts);
    }
}
