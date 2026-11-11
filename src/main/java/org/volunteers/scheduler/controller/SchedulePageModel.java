package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.service.SchedulerService;

@org.springframework.stereotype.Component
public class SchedulePageModel extends PageModelSupport {
    public SchedulePageModel(SchedulerService service, org.volunteers.scheduler.service.PhoneAccess access) {
        super(service, access);
    }
    String schedule(long eventId, Long edit,
                    boolean create,
                    String view,
                    Long departmentId, LocalDate date,
                    int page,
                    Model model, HttpSession session, HttpServletRequest request) {

        if (request.getParameter("departmentId") == null) departmentId = access.department(session, eventId);
        if (request.getParameter("view") == null && !access.canManage(session, eventId)
                && access.ownVolunteerId(session, eventId) != null) view = "mine";
        if (!Set.of("all", "mine", "needs").contains(view)) view = "all";
        var result = service.scheduleList(eventId, departmentId, date, view, access.ids(session), page, edit);
        var data = result.snapshot();
        prepare(data, model, session);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("date", date);
        model.addAttribute("scheduleView", view);
        var eventDays = new TreeSet<LocalDate>();
        data.event().getStartDate().datesUntil(data.event().getEndDate().plusDays(1)).forEach(eventDays::add);
        eventDays.addAll(result.dates());
        model.addAttribute("eventDays", eventDays);
        var shiftPage = result.page();
        model.addAttribute("listPage", shiftPage);
        model.addAttribute("rows", shiftPage.getContent());
        Map<LocalDate, List<SchedulerService.ShiftRow>> dayGroups = new LinkedHashMap<>();
        shiftPage.getContent().forEach(row -> dayGroups.computeIfAbsent(row.shift().getDate(), ignored -> new ArrayList<>()).add(row));
        model.addAttribute("dayGroups", dayGroups);
        model.addAttribute("assignmentOptions", result.options());
        Map<Long, Set<Long>> assignedVolunteerIds = new HashMap<>();
        shiftPage.getContent().forEach(row -> assignedVolunteerIds.put(row.shift().getId(),
            row.assignments().stream().map(a -> a.getVolunteer().getId()).collect(java.util.stream.Collectors.toSet())));
        model.addAttribute("assignedVolunteerIds", assignedVolunteerIds);
        Map<String, String> defaults = new HashMap<>(Map.of("date", data.event().getStartDate().toString(), "requiredCount", "1"));
        if (departmentId != null) defaults.put("departmentId", departmentId.toString());
        if (edit != null) {
            Shift s = data.shifts().stream().filter(item -> item.getId().equals(edit)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
            defaults.putAll(Map.of("name", s.getName(), "departmentId", s.getDepartment().getId().toString(), "date", s.getDate().toString(),
                    "startTime", s.getStartTime().toString(), "endTime", s.getEndTime().toString(),
                    "requiredCount", Integer.toString(s.getRequiredCount()), "notes", s.getNotes()));
        }
        draft(model, defaults); model.addAttribute("editId", edit);
        model.addAttribute("shiftTimes", java.util.stream.IntStream.rangeClosed(0, 46)
                .mapToObj(i -> java.time.LocalTime.of(6, 0).plusMinutes(i * 15L)).toList());
        model.addAttribute("showEditor", create || edit != null || model.containsAttribute("error") && model.containsAttribute("draft") && !model.containsAttribute("pending"));
        return Boolean.TRUE.equals(model.getAttribute("showEditor")) ? "pages/schedule-editor" : "pages/schedule";
    }
}
