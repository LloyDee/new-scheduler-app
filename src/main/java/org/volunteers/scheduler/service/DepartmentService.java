package org.volunteers.scheduler.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.repository.Departments;
import org.volunteers.scheduler.repository.Shifts;
import org.volunteers.scheduler.repository.Volunteers;

import static org.volunteers.scheduler.service.SchedulerService.*;

@Service
@Transactional(readOnly = true)
public class DepartmentService {
    private final Volunteers volunteers;
    private final Departments departments;
    private final Shifts shifts;
    private final ScheduleService scheduleService;
    private final SchedulerQueries schedulerQueries;

    public DepartmentService(Volunteers volunteers, Departments departments, Shifts shifts, ScheduleService scheduleService, SchedulerQueries schedulerQueries) {
        this.volunteers = volunteers;
        this.departments = departments;
        this.shifts = shifts;
        this.scheduleService = scheduleService;
        this.schedulerQueries = schedulerQueries;
    }

    @Transactional
    public Department saveDepartment(long eventId, Long id, Map<String, String> input) {
        Event e = schedulerQueries.event(eventId);
        String name = required(input, "name", 160), description = optional(input, "description", 2000);
        Department d = id == null ? new Department() : schedulerQueries.department(eventId, id);
        d.setEvent(e); d.setName(name); d.setDescription(description);
        return departments.save(d);
    }

    @Transactional
    public void deleteDepartment(long eventId, long id) {
        Department d = schedulerQueries.department(eventId, id);
        volunteers.findByEventIdOrderByNameAscIdAsc(eventId).forEach(v ->
                v.getDepartmentRoles().keySet().removeIf(selected -> selected.getId().equals(id)));
        volunteers.flush();
        shifts.findByDepartmentEventIdOrderByDateAscStartTimeAscIdAsc(eventId).stream()
                .filter(s -> s.getDepartment().getId().equals(id)).toList().forEach(s -> scheduleService.deleteShift(eventId, s.getId()));
        departments.delete(d); departments.flush();
    }
}
