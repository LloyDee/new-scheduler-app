package org.volunteers.scheduler.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/** Compatibility facade for existing callers; workflows live in the feature services. */
@Service
@Transactional(readOnly = true)
public class SchedulerService {
    private final EventService eventService;
    private final VolunteerService volunteerService;
    private final DepartmentService departmentService;
    private final ScheduleService scheduleService;
    private final SchedulerQueries schedulerQueries;

    public SchedulerService(EventService eventService, VolunteerService volunteerService, DepartmentService departmentService, ScheduleService scheduleService, SchedulerQueries schedulerQueries) {
        this.eventService = eventService;
        this.volunteerService = volunteerService;
        this.departmentService = departmentService;
        this.scheduleService = scheduleService;
        this.schedulerQueries = schedulerQueries;
    }

    public static final String LAST_ADMIN = "This event must retain at least one administrator. Assign another administrator before removing this one.";
    @Transactional
    public Volunteer ensureAdministrator(Event event, String phone) {
        return eventService.ensureAdministrator(event, phone);
    }
    public List<Event> events() {
        return eventService.events();
    }
    public org.springframework.data.domain.Page<Event> eventsPage(int page) {
        return eventService.eventsPage(page);
    }
    public record VolunteerPage(Snapshot snapshot, org.springframework.data.domain.Page<Volunteer> page) { }

    public VolunteerPage volunteerPage(long eventId, Long departmentId, String search, int page, List<Long> details) {
        return volunteerService.volunteerPage(eventId, departmentId, search, page, details);
    }
    public VolunteerPage volunteerList(long eventId, Long departmentId, String search, int page, List<Long> details, boolean histories) {
        return volunteerService.volunteerPage(eventId, departmentId, search, page, details, histories);
    }
    public Event event(long id) {
        return schedulerQueries.event(id);
    }
    public Volunteer volunteer(long eventId, long id) {
        return schedulerQueries.volunteer(eventId, id);
    }
    public Department department(long eventId, long id) {
        return schedulerQueries.department(eventId, id);
    }
    public Shift shift(long eventId, long id) {
        return schedulerQueries.shift(eventId, id);
    }
    public SchedulerQueries.SchedulePage schedulePage(long eventId, Long departmentId, LocalDate date, String view, Set<Long> ownIds, int page, Long edit) {
        return schedulerQueries.schedulePage(eventId, departmentId, date, view, ownIds, page, edit);
    }
    public SchedulerQueries.DepartmentPage departmentPage(long eventId, int page, List<Long> details) {
        return schedulerQueries.departmentPage(eventId, page, details);
    }
    public SchedulerQueries.Home home(long eventId, Set<Long> ownIds, java.time.LocalDateTime now) {
        return schedulerQueries.home(eventId, ownIds, now);
    }    public org.volunteers.scheduler.service.SchedulerQueries.SchedulePage scheduleList(long eventId, Long departmentId, LocalDate date, String view, Set<Long> ownIds, int page, Long edit) {
        return schedulerQueries.schedulePage(eventId, departmentId, date, view, ownIds, page, edit, false);
    }
    public org.volunteers.scheduler.service.SchedulerQueries.DepartmentPage departmentList(long eventId, int page, List<Long> details) {
        return schedulerQueries.departmentPage(eventId, page, details, false);
    }
    public Snapshot snapshot(long id) {
        return schedulerQueries.snapshot(id);
    }

    @Transactional
    public Event saveEvent(Long id, Map<String, String> input) {
        return eventService.saveEvent(id, input);
    }

    @Transactional
    public Event saveEvent(Long id, Map<String, String> input, String administratorPhone) {
        return eventService.saveEvent(id, input, administratorPhone);
    }

    @Transactional
    public Volunteer saveVolunteer(long eventId, Long id, Map<String, String> input) {
        return volunteerService.saveVolunteer(eventId, id, input);
    }

    /** Validate changes to administrator memberships under the same lock as to write. */
    @Transactional
    public Volunteer saveVolunteer(long eventId, Long id, Map<String, String> input, boolean mayAdminister) {
        return volunteerService.saveVolunteer(eventId, id, input, mayAdminister);
    }

    @Transactional
    public Department saveDepartment(long eventId, Long id, Map<String, String> input) {
        return departmentService.saveDepartment(eventId, id, input);
    }

    @Transactional
    public Shift saveShift(long eventId, Long id, Map<String, String> input) {
        return scheduleService.saveShift(eventId, id, input);
    }

    @Transactional
    public void assign(long eventId, long shiftId, long volunteerId, boolean confirmed) {
        scheduleService.assign(eventId, shiftId, volunteerId, confirmed);
    }

    @Transactional
    public void assignBatch(long eventId, long shiftId, List<Long> volunteerIds) {
        scheduleService.assignBatch(eventId, shiftId, volunteerIds);
    }

    @Transactional
    public void updateAssignments(long eventId, long shiftId, List<Long> volunteerIds) {
        scheduleService.updateAssignments(eventId, shiftId, volunteerIds);
    }

    @Transactional
    public void unassign(long eventId, long id) {
        scheduleService.unassign(eventId, id);
    }

    @Transactional
    public void deleteVolunteer(long eventId, long id) {
        volunteerService.deleteVolunteer(eventId, id);
    }

    @Transactional
    public void deleteVolunteer(long eventId, long id, boolean mayAdminister) {
        volunteerService.deleteVolunteer(eventId, id, mayAdminister);
    }

    @Transactional
    public void deleteShift(long eventId, long id) {
        scheduleService.deleteShift(eventId, id);
    }

    @Transactional
    public void deleteDepartment(long eventId, long id) {
        departmentService.deleteDepartment(eventId, id);
    }

    @Transactional
    public void deleteEvent(long id) {
        eventService.deleteEvent(id);
    }

    static boolean overlaps(Shift s, LocalDate date, LocalTime start, LocalTime end) {
        return s.getDate().equals(date) && start.isBefore(s.getEndTime()) && end.isAfter(s.getStartTime());
    }
    static String conflictDescription(Assignment a) {
        return a.getVolunteer().getName() + " — " + a.getShift().getDepartment().getName() + ", " + a.getShift().getDate() + " "
                + a.getShift().getStartTime() + "–" + a.getShift().getEndTime();
    }
    public static boolean confirmed(Map<String, String> input) { return "true".equals(input.get("confirmOverlap")); }
    public static long number(Map<String, String> input, String key) {
        try { return Long.parseLong(input.getOrDefault(key, "")); }
        catch (NumberFormatException ex) { throw invalid("Enter a valid value for " + key + "."); }
    }
    static LocalDate date(Map<String, String> input, String key) {
        try { return LocalDate.parse(input.getOrDefault(key, "")); }
        catch (DateTimeParseException ex) { throw invalid("Enter a valid date for " + key + "."); }
    }
    static LocalTime time(Map<String, String> input, String key) {
        try { return LocalTime.parse(input.getOrDefault(key, "")); }
        catch (DateTimeParseException ex) { throw invalid("Enter a valid time for " + key + "."); }
    }
    static String required(Map<String, String> input, String key, int max) {
        String value = optional(input, key, max); require(!value.isBlank(), "Please enter " + key + "."); return value;
    }
    static String optional(Map<String, String> input, String key, int max) {
        String value = input.getOrDefault(key, "").strip();
        require(value.length() <= max, key + " must be at most " + max + " characters."); return value;
    }
    static void sameEvent(long expected, long actual) { if (expected != actual) throw notFound(); }
    static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in this event."); }
    public static class OverlapException extends IllegalArgumentException {
        public OverlapException(String conflicts) { super("Overlapping assignments: " + conflicts); }
    }

    public record Snapshot(Event event, List<Volunteer> volunteers, List<Department> departments,
                           List<Shift> shifts, List<Assignment> assignments) {
        public List<Volunteer> availableVolunteers(Shift shift) {
            Set<Long> busy = new HashSet<>();
            assignments.stream().filter(a -> overlaps(a.getShift(), shift.getDate(), shift.getStartTime(), shift.getEndTime()))
                    .forEach(a -> busy.add(a.getVolunteer().getId()));
            return volunteers.stream().filter(v -> !busy.contains(v.getId()))
                    .filter(v -> v.getDepartmentRoles().isEmpty() || v.getDepartmentRoles().keySet().stream()
                            .anyMatch(d -> d.getId().equals(shift.getDepartment().getId()))).toList();
        }

        public List<ShiftRow> rows() {
            Map<Long, List<Assignment>> byShift = new HashMap<>();
            assignments.forEach(a -> byShift.computeIfAbsent(a.getShift().getId(), ignored -> new ArrayList<>()).add(a));
            return shifts.stream().map(s -> new ShiftRow(s, byShift.getOrDefault(s.getId(), List.of()))).toList();
        }
    }
    public record ShiftRow(Shift shift, List<Assignment> assignments) {
        public List<Assignment> previewAssignments(Set<Long> ownIds) {
            if (assignments.size() <= 6) return assignments;
            var preview = new ArrayList<>(assignments.subList(0, 5));
            assignments.stream().filter(a -> ownIds.contains(a.getVolunteer().getId())).findFirst()
                    .ifPresent(a -> { if (!preview.contains(a)) preview.set(4, a); });
            return preview;
        }
        public int getMissing() { return Math.max(0, shift.getRequiredCount() - assignments.size()); }
        public String getStatus() {
            return assignments.size() < shift.getRequiredCount() ? "Underfilled"
                    : assignments.size() == shift.getRequiredCount() ? "Filled" : "Overfilled";
        }
        public String getStatusClass() { return getMissing() > 0 ? "warning" : "success"; }
    }
}
