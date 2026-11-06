package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.service.*;

@Controller
public class AnnouncementController {
    private final AnnouncementService announcements;
    private final SchedulerService scheduler;
    public AnnouncementController(AnnouncementService announcements, SchedulerService scheduler) {
        this.announcements = announcements; this.scheduler = scheduler;
    }
    @GetMapping("/events/{eventId}/announcements")
    String page(@PathVariable long eventId, Model model, HttpSession session) {
        model.addAttribute("event", scheduler.event(eventId)); model.addAttribute("base", "/events/" + eventId);
        session.setAttribute("activeEvent", eventId);
        model.addAttribute("announcementDepartments", announcements.allowed(eventId, session));
        model.addAttribute("announcementHistory", announcements.history(eventId, session));
        return "pages/announcements";
    }
    @GetMapping("/events/{eventId}/announcements/audience") @ResponseBody
    Map<String, Integer> audience(@PathVariable long eventId, @RequestParam long departmentId, HttpSession session) {
        return Map.of("count", announcements.preview(eventId, departmentId, session));
    }
    @PostMapping("/events/{eventId}/announcements")
    String send(@PathVariable long eventId, @RequestParam long departmentId, @RequestParam String title,
                @RequestParam String message, @RequestParam(defaultValue = "false") boolean acknowledgmentRequired,
                HttpSession session, RedirectAttributes flash) {
        try {
            announcements.send(eventId, departmentId, title, message, acknowledgmentRequired, session);
            flash.addFlashAttribute("notice", "Announcement sent.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("announcementError", ex.getMessage());
            flash.addFlashAttribute("draftTitle", title); flash.addFlashAttribute("draftMessage", message);
            flash.addFlashAttribute("draftDepartment", departmentId); flash.addFlashAttribute("draftRequired", acknowledgmentRequired);
        }
        return "redirect:/events/" + eventId + "/announcements";
    }
}
