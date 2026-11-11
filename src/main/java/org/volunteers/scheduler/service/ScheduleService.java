package org.volunteers.scheduler.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.Assignment;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.repository.Assignments;
import org.volunteers.scheduler.repository.Events;
import org.volunteers.scheduler.repository.Shifts;
import org.volunteers.scheduler.repository.Volunteers;

import static org.volunteers.scheduler.service.SchedulerService.*;

@Service
@Transactional(readOnly = true)
public class ScheduleService {
    private final Events events;
    private final Volunteers volunteers;
    private final Shifts shifts;
    private final Assignments assignments;
    private final SchedulerQueries schedulerQueries;

    public ScheduleService(Events events, Volunteers volunteers, Shifts shifts, Assignments assignments, SchedulerQueries schedulerQueries) {
        this.events = events;
        this.volunteers = volunteers;
        this.shifts = shifts;
        this.assignments = assignments;
        this.schedulerQueries = schedulerQueries;
    }

    @Transactional
    public Shift saveShift(long eventId, Long id, Map<String, String> input) {
        Event e = schedulerQueries.event(eventId);
        Department d = schedulerQueries.department(eventId, number(input, "departmentId"));
        LocalDate date = e.getType() == Event.Type.CIRCUIT_ASSEMBLY ? e.getStartDate() : date(input, "date");
        LocalTime start = time(input, "startTime"), end = time(input, "endTime");
        if (e.getType() == Event.Type.CIRCUIT_ASSEMBLY) {
            for (LocalTime value : List.of(start, end)) {
                require(!value.isBefore(LocalTime.of(6, 0)) && !value.isAfter(LocalTime.of(17, 30)),
                        "Shift times must be between 06:00 AM and 05:30 PM.");
                require(value.getMinute() % 15 == 0 && value.getSecond() == 0 && value.getNano() == 0,
                        "Shift times must use 15-minute increments.");
            }
        }
        require(!date.isBefore(e.getStartDate()) && !date.isAfter(e.getEndDate()), "Shift date must be within the event dates.");
        require(end.isAfter(start), "End time must be after start time. Overnight shifts are not supported.");
        long required = number(input, "requiredCount");
        require(required >= 1 && required <= 10000, "Required volunteers must be between 1 and 10,000.");
        String notes = optional(input, "notes", 2000);
        String name = required(input, "name", 160);
        Shift s = id == null ? new Shift() : schedulerQueries.shift(eventId, id);
        if (id != null && !(date.equals(s.getDate()) && start.equals(s.getStartTime()) && end.equals(s.getEndTime()))) {
            List<Assignment> all = assignments.findByShiftDepartmentEventIdOrderByIdAsc(eventId);
            Set<Long> assigned = new HashSet<>();
            all.stream().filter(a -> a.getShift().getId().equals(id)).forEach(a -> assigned.add(a.getVolunteer().getId()));
            List<String> conflicts = all.stream().filter(a -> assigned.contains(a.getVolunteer().getId()) && !a.getShift().getId().equals(id)
                    && overlaps(a.getShift(), date, start, end)).map(SchedulerService::conflictDescription).toList();
            if (!conflicts.isEmpty() && !confirmed(input)) throw new OverlapException(String.join("; ", conflicts));
        }
        s.setName(name); s.setDepartment(d); s.setDate(date); s.setStartTime(start); s.setEndTime(end);
        s.setRequiredCount((int) required); s.setNotes(notes);
        return shifts.save(s);
    }

    @Transactional
    public void assign(long eventId, long shiftId, long volunteerId, boolean confirmed) {
        events.lockForVolunteerUpdate(eventId).orElseThrow(SchedulerService::notFound);
        Shift s = schedulerQueries.shift(eventId, shiftId);
        Volunteer v = schedulerQueries.volunteer(eventId, volunteerId);
        require(!assignments.existsByShiftIdAndVolunteerId(shiftId, volunteerId), "This volunteer is already assigned to this shift.");
        List<String> conflicts = assignments.findByShiftDepartmentEventIdOrderByIdAsc(eventId).stream()
                .filter(a -> a.getVolunteer().getId().equals(volunteerId) && overlaps(a.getShift(), s.getDate(), s.getStartTime(), s.getEndTime()))
                .map(SchedulerService::conflictDescription).toList();
        if (!conflicts.isEmpty() && !confirmed) throw new OverlapException(String.join("; ", conflicts));
        if (v.getDepartmentRoles().isEmpty()) v.getDepartmentRoles().put(s.getDepartment(), Volunteer.Role.VOLUNTEER);
        Assignment a = new Assignment(); a.setShift(s); a.setVolunteer(v); assignments.saveAndFlush(a);
    }

    @Transactional
    public void assignBatch(long eventId, long shiftId, List<Long> volunteerIds) {
        events.lockForVolunteerUpdate(eventId).orElseThrow(SchedulerService::notFound);
        Shift target = schedulerQueries.shift(eventId, shiftId);
        require(volunteerIds != null && !volunteerIds.isEmpty(), "Choose at least one volunteer.");
        require(new HashSet<>(volunteerIds).size() == volunteerIds.size(), "Choose each volunteer only once.");
        Set<Long> eligible = new HashSet<>();
        schedulerQueries.snapshot(eventId).availableVolunteers(target).stream()
                .forEach(v -> eligible.add(v.getId()));
        require(eligible.containsAll(volunteerIds),
                "One or more selected volunteers are no longer available in this department. Please choose again.");
        for (Long volunteerId : volunteerIds) assign(eventId, shiftId, volunteerId, false);
    }

    @Transactional
    public void updateAssignments(long eventId, long shiftId, List<Long> volunteerIds) {
        events.lockForVolunteerUpdate(eventId).orElseThrow(SchedulerService::notFound);
        Shift target = schedulerQueries.shift(eventId, shiftId);
        require(volunteerIds != null, "Choose volunteers to save.");
        Set<Long> selected = new HashSet<>(volunteerIds);
        require(selected.size() == volunteerIds.size(), "Choose each volunteer only once.");
        var data = schedulerQueries.snapshot(eventId);
        var current = data.assignments().stream()
                .filter(a -> a.getShift().getId().equals(shiftId)).toList();
        Set<Long> currentIds = new HashSet<>();
        current.forEach(a -> currentIds.add(a.getVolunteer().getId()));
        Set<Long> eligible = new HashSet<>(currentIds);
        data.availableVolunteers(target).stream()
                .forEach(v -> eligible.add(v.getId()));
        require(eligible.containsAll(selected),
                "One or more selected volunteers are no longer available in this department. Please choose again.");
        for (Long volunteerId : selected) {
            if (!currentIds.contains(volunteerId)) assign(eventId, shiftId, volunteerId, false);
        }
        assignments.deleteAll(current.stream()
                .filter(a -> !selected.contains(a.getVolunteer().getId())).toList());
    }

    @Transactional
    public void unassign(long eventId, long id) {
        Assignment a = assignments.findById(id).orElseThrow(SchedulerService::notFound);
        sameEvent(eventId, a.getShift().getDepartment().getEvent().getId()); assignments.delete(a);
    }

    @Transactional
    public void deleteShift(long eventId, long id) {
        Shift s = schedulerQueries.shift(eventId, id);
        assignments.deleteAll(assignments.findByShiftDepartmentEventIdOrderByIdAsc(eventId).stream()
                .filter(a -> a.getShift().getId().equals(id)).toList());
        assignments.flush(); shifts.delete(s); shifts.flush();
    }
}
