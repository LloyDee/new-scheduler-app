package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.volunteers.scheduler.service.PhoneAccess;
import org.volunteers.scheduler.service.SchedulerQueries;

/** Event-scoped fragments used only when a detail dialog is opened. */
@Controller
public class DialogController {
    private final SchedulerQueries queries;
    private final PhoneAccess access;
    public DialogController(SchedulerQueries queries, PhoneAccess access) {
        this.queries = queries; this.access = access;
    }
    private void prepare(long eventId, Model model, HttpServletResponse response) {
        model.addAttribute("base", "/events/" + eventId);
        model.addAttribute("timePattern", "h:mm a");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Scheduler-Detail", "true");
    }
    @GetMapping("/events/{eventId}/volunteers/{id}/detail")
    String volunteer(@PathVariable long eventId, @PathVariable long id, Model model, HttpServletResponse response) {
        var data = queries.volunteerDetails(eventId, id);
        prepare(eventId, model, response);
        model.addAttribute("v", data.volunteers().getFirst());
        model.addAttribute("volunteerAssignments", Map.of(id, data.rows()));
        return "shared/dialog-details :: volunteer";
    }
    @GetMapping("/events/{eventId}/departments/{id}/detail")
    String department(@PathVariable long eventId, @PathVariable long id, Model model, HttpServletResponse response) {
        var data = queries.departmentDetails(eventId, id);
        prepare(eventId, model, response);
        model.addAttribute("d", data.departments().getFirst());
        model.addAttribute("departmentSchedules", Map.of(id, data.rows()));
        return "shared/dialog-details :: department";
    }
    @GetMapping({"/events/{eventId}/schedule/{id}/detail", "/events/{eventId}/schedule/{id}/team"})
    String shift(@PathVariable long eventId, @PathVariable long id, Model model, HttpServletResponse response,
                 jakarta.servlet.http.HttpServletRequest request) {
        var data = queries.shiftDetails(eventId, id, false);
        prepare(eventId, model, response);
        model.addAttribute("row", data.rows().getFirst());
        return "shared/dialog-details :: " + (request.getRequestURI().endsWith("/team") ? "team" : "shift");
    }
    @GetMapping("/events/{eventId}/schedule/{id}/options")
    String options(@PathVariable long eventId, @PathVariable long id, Model model, HttpSession session, HttpServletResponse response) {
        if (!access.canManage(session, eventId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var data = queries.shiftDetails(eventId, id, true);
        prepare(eventId, model, response);
        var row = data.rows().getFirst();
        model.addAttribute("row", row);
        model.addAttribute("assignmentOptions", Map.of(id, data.volunteers()));
        model.addAttribute("assignedVolunteerIds", Map.of(id, row.assignments().stream().map(a -> a.getVolunteer().getId()).collect(java.util.stream.Collectors.toSet())));
        return "shared/dialog-details :: assignment";
    }
}
