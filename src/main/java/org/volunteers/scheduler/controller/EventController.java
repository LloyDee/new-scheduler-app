package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.service.EventService;
import org.volunteers.scheduler.service.SchedulerQueries;

import static org.volunteers.scheduler.controller.WebActions.*;

@Controller
public class EventController {
    private final EventService service;
    private final SchedulerQueries queries;
    private final org.volunteers.scheduler.service.PhoneAccess access;
    private final EventPageModel eventPageModel;
    public EventController(SchedulerQueries queries, org.volunteers.scheduler.service.PhoneAccess access, EventService service, EventPageModel eventPageModel) {
        this.access = access;
        this.service = service;
        this.queries = queries;
        this.eventPageModel = eventPageModel;
    }
    @GetMapping("/events")
    String events(@RequestParam(required = false) Long edit,
                  @RequestParam(defaultValue = "false") boolean create, @RequestParam(defaultValue = "0") int page,
                  Model model, HttpSession session) {
        return eventPageModel.events(edit, create, page, model, session);
    }

    @PostMapping("/events/save")
    String saveEvent(@RequestParam(required = false) Long id, @RequestParam Map<String, String> input,
                     RedirectAttributes flash, HttpSession session) {
        return attempt(() -> {
            Event e = access.saveEvent(id, input, session); session.setAttribute("activeEvent", e.getId());
            return "/events/" + e.getId() + "/home";
        }, id == null ? "/events?create=true" : "/events" + editQuery(id), input, flash, "Event saved.");
    }

    @PostMapping("/events/{id}/select")
    String select(@PathVariable long id, HttpSession session) {
        queries.event(id); session.setAttribute("activeEvent", id); return "redirect:/events/" + id + "/home";
    }

    @PostMapping("/events/{id}/delete")
    String deleteEvent(@PathVariable long id, HttpSession session, RedirectAttributes flash) {
        service.deleteEvent(id);
        if (Objects.equals(session.getAttribute("activeEvent"), id)) session.removeAttribute("activeEvent");
        flash.addFlashAttribute("notice", "Event and its data deleted."); return "redirect:/events";
    }

    @GetMapping("/events/{eventId}/home")
    String home(@PathVariable long eventId, Model model, HttpSession session) {
        return eventPageModel.home(eventId, model, session);
    }

    @GetMapping("/events/{eventId}/more")
    String more(@PathVariable long eventId, Model model, HttpSession session) {
        return eventPageModel.more(eventId, model, session);
    }
}
