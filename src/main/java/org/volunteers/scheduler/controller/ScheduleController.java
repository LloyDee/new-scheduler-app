package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.model.Assignment;
import org.volunteers.scheduler.model.Shift;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.ScheduleService;
import org.volunteers.scheduler.service.SchedulerService;

import static org.volunteers.scheduler.controller.WebActions.*;

@Controller
public class ScheduleController {
    private final ScheduleService service;
    private final SchedulePageModel schedulePageModel;
    public ScheduleController(ScheduleService service, SchedulePageModel schedulePageModel) {
        this.service = service;
        this.schedulePageModel = schedulePageModel;
    }
    @GetMapping("/events/{eventId}/schedule")
    String schedule(@PathVariable long eventId, @RequestParam(required = false) Long edit,
                    @RequestParam(defaultValue = "false") boolean create,
                    @RequestParam(defaultValue = "all") String view,
                    @RequestParam(required = false) Long departmentId, @RequestParam(required = false) LocalDate date,
                    @RequestParam(defaultValue = "0") int page,
                    Model model, HttpSession session, HttpServletRequest request) {
        return schedulePageModel.schedule(eventId, edit, create, view, departmentId, date, page, model, session, request);
    }

    @PostMapping("/events/{eventId}/schedule/save")
    String saveShift(@PathVariable long eventId, @RequestParam(required = false) Long id,
                     @RequestParam Map<String, String> input, RedirectAttributes flash) {
        String base = "/events/" + eventId + "/schedule";
        return attempt(() -> { service.saveShift(eventId, id, input); return base; },
                base + editQuery(id), input, flash, "Shift saved.");
    }

    @PostMapping("/events/{eventId}/schedule/{id}/delete")
    String deleteShift(@PathVariable long eventId, @PathVariable long id, RedirectAttributes flash) {
        service.deleteShift(eventId, id); flash.addFlashAttribute("notice", "Shift and its assignments deleted.");
        return "redirect:/events/" + eventId + "/schedule";
    }

    @PostMapping("/events/{eventId}/schedule/{id}/assign")
    String assign(@PathVariable long eventId, @PathVariable long id, @RequestParam Map<String, String> input,
                  RedirectAttributes flash) {
        String base = "/events/" + eventId + "/schedule";
        try {
            service.assign(eventId, id, SchedulerService.number(input, "volunteerId"), SchedulerService.confirmed(input));
            flash.addFlashAttribute("notice", "Volunteer assigned.");
        } catch (SchedulerService.OverlapException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("pending", Map.of("shiftId", Long.toString(id), "volunteerId", input.get("volunteerId")));
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:" + base + "#shift-" + id;
    }

    @PostMapping("/events/{eventId}/schedule/{id}/assign-batch")
    String assignBatch(@PathVariable long eventId, @PathVariable long id,
                       @RequestParam(required = false) List<Long> volunteerIds, RedirectAttributes flash) {
        try {
            service.assignBatch(eventId, id, volunteerIds);
            flash.addFlashAttribute("notice", "Volunteers assigned.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/events/" + eventId + "/schedule#shift-" + id;
    }

    @PostMapping("/events/{eventId}/schedule/{id}/assignments")
    String updateAssignments(@PathVariable long eventId, @PathVariable long id,
                             @RequestParam(required = false) List<Long> volunteerIds, RedirectAttributes flash) {
        try {
            service.updateAssignments(eventId, id, volunteerIds == null ? List.of() : volunteerIds);
            flash.addFlashAttribute("notice", "Volunteers updated.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/events/" + eventId + "/schedule#shift-" + id;
    }

    @PostMapping("/events/{eventId}/assignments/{id}/delete")
    String unassign(@PathVariable long eventId, @PathVariable long id, RedirectAttributes flash) {
        service.unassign(eventId, id); flash.addFlashAttribute("notice", "Assignment removed.");
        return "redirect:/events/" + eventId + "/schedule";
    }
}
