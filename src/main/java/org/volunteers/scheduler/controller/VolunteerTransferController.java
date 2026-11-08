package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.*;

@Controller
public class VolunteerTransferController {
    private final VolunteerTransferService transfers;
    private final SchedulerService scheduler;
    public VolunteerTransferController(VolunteerTransferService transfers, SchedulerService scheduler) {
        this.transfers = transfers; this.scheduler = scheduler;
    }
    @GetMapping("/events/{eventId}/volunteers/transfer")
    String form(@PathVariable long eventId, @RequestParam(required = false) List<Long> ids,
                @RequestParam(required = false) Long destination,
                @RequestParam(defaultValue = "") String q,
                @RequestParam(required = false) Long departmentId,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "ADD") VolunteerTransferService.Action action,
                @RequestParam(defaultValue = "false") boolean review,
                Model model, HttpSession session) {
        transfers.authorize(eventId, session);
        ids = ids == null ? List.of() : ids.stream().distinct().toList();
        if (ids.size() > 500) throw new IllegalArgumentException("Select up to 500 volunteers.");
        var candidates = scheduler.volunteerPage(eventId, departmentId, q, page, ids);
        model.addAttribute("listPage", candidates.page());
        Map<Long, String> selectionNames = new HashMap<>();
        candidates.page().forEach(v -> {
            String[] parts = v.getName().strip().split("\\s+");
            selectionNames.put(v.getId(), parts.length > 1 ? parts[0] + " " + parts[parts.length - 1] : parts[0]);
        });
        model.addAttribute("selectionNames", selectionNames);
        model.addAttribute("departments", candidates.snapshot().departments());
        model.addAttribute("q", q); model.addAttribute("departmentId", departmentId);
        var visibleIds = candidates.page().getContent().stream().map(Volunteer::getId).toList();
        model.addAttribute("retainedIds", ids.stream().filter(id -> !visibleIds.contains(id)).toList());
        model.addAttribute("event", scheduler.event(eventId));
        model.addAttribute("ids", ids); model.addAttribute("destinations", transfers.destinations(eventId, session));
        model.addAttribute("destination", destination);
        model.addAttribute("action", action.name());
        model.addAttribute("selectedVolunteers", ids.stream().distinct().map(id -> scheduler.volunteer(eventId, id)).toList());
        if (review) {
            if (ids.isEmpty() || destination == null) {
                String warning = ids.isEmpty() && destination == null
                    ? "Select at least one volunteer and a destination event before reviewing."
                    : ids.isEmpty() ? "Select at least one volunteer before reviewing."
                    : "Select a destination event before reviewing.";
                model.addAttribute("reviewWarning", warning);
                return "pages/volunteer-transfer";
            }
            var result = transfers.review(eventId, destination, ids, action, session);
            model.addAttribute("review", result);
            model.addAttribute("blocked", result.rows().stream().anyMatch(r -> !Set.of("Ready", "Already added").contains(r.status())));
        }
        return "pages/volunteer-transfer";
    }
    @PostMapping("/events/{eventId}/volunteers/transfer")
    String confirm(@PathVariable long eventId, @RequestParam List<Long> ids,
                   @RequestParam long destination,
                   @RequestParam(defaultValue = "ADD") VolunteerTransferService.Action action,
                   HttpSession session, RedirectAttributes flash) {
        try {
            transfers.execute(eventId, destination, ids, action, session);
            flash.addFlashAttribute("notice", "Add/Move completed. Volunteers already added were left unchanged.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/events/" + eventId + "/volunteers";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    String invalid(IllegalArgumentException ex, RedirectAttributes flash,
                   jakarta.servlet.http.HttpServletRequest request) {
        flash.addFlashAttribute("error", ex.getMessage());
        String path = request.getRequestURI();
        return "redirect:" + path.substring(request.getContextPath().length(), path.lastIndexOf("/transfer"));
    }
}
