package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.volunteers.scheduler.service.SchedulerService;

@ControllerAdvice(assignableTypes = {WebController.class, EventController.class, VolunteerController.class, DepartmentController.class, ScheduleController.class, ReportController.class})
class CommonPageAdvice {
    private final SchedulerService service;
    CommonPageAdvice(SchedulerService service) { this.service = service; }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalidRequest(Model model) {
        model.addAttribute("appVersion", new org.volunteers.scheduler.version.Version());
        model.addAttribute("status", 400); return "error";
    }
    @ModelAttribute
    void common(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("event", null);
        Object id = session.getAttribute("activeEvent");
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        boolean eventPage = path.equals("/events") || path.matches("/events/\\d+/(home|volunteers|departments|schedule|reports|more)");
        if (id instanceof Long eventId && !eventPage && Set.of("GET", "HEAD").contains(request.getMethod())) {
            try { model.addAttribute("event", service.event(eventId)); }
            catch (org.springframework.web.server.ResponseStatusException ex) { session.removeAttribute("activeEvent"); }
        }
        model.addAttribute("timePattern", "h:mm a");
    }
}
