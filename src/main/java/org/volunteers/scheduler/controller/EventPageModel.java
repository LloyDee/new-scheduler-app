package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.ui.Model;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.SchedulerService;

@org.springframework.stereotype.Component
public class EventPageModel extends PageModelSupport {
    public EventPageModel(SchedulerService service, org.volunteers.scheduler.service.PhoneAccess access) {
        super(service, access);
    }
    String events(Long edit,
                  boolean create, int page,
                  Model model, HttpSession session) {
        var eventPage = service.eventsPage(page);
        var events = eventPage.getContent();
        Set<Long> manageable = new HashSet<>(), administrable = new HashSet<>();
        for (Event listed : events) {
            if (access.canManage(session, listed.getId())) manageable.add(listed.getId());
            if (access.canAdminister(session, listed.getId())) administrable.add(listed.getId());
        }
        model.addAttribute("manageableEventIds", manageable);
        model.addAttribute("administrableEventIds", administrable);
        model.addAttribute("listPage", eventPage);
        model.addAttribute("events", events); model.addAttribute("types", Event.Type.values());
        Event active = events.stream().filter(e -> e.getId().equals(session.getAttribute("activeEvent"))).findFirst().orElse(null);
        if (active == null && session.getAttribute("activeEvent") instanceof Long activeId) {
            try { active = service.event(activeId); }
            catch (org.springframework.web.server.ResponseStatusException ex) { session.removeAttribute("activeEvent"); }
        }
        model.addAttribute("event", active);
        Map<String, String> defaults = new HashMap<>();
        if (edit != null) {
            Event e = events.stream().filter(item -> item.getId().equals(edit)).findFirst().orElseGet(() -> service.event(edit));
            defaults.putAll(Map.of("name", e.getName(), "type", e.getType().name(), "startDate", e.getStartDate().toString(),
                    "endDate", e.getEndDate().toString(), "venue", e.getVenue()));
            defaults.put("assembly", e.getAssembly() == null ? "" : e.getAssembly());
            defaults.put("circuit", e.getCircuit() == null ? "" : e.getCircuit());
        }
        draft(model, defaults); model.addAttribute("editId", edit);
        model.addAttribute("showEditor", create || edit != null || model.containsAttribute("error"));
        return Boolean.TRUE.equals(model.getAttribute("showEditor")) ? "pages/events-editor" : "pages/events";
    }
    String home(long eventId, Model model, HttpSession session) {
        LocalDateTime now = LocalDateTime.now();
        var result = service.home(eventId, access.ids(session), now);
        var data = result.snapshot();
        prepare(data, model, session);
        model.addAttribute("missing", result.missing());
        model.addAttribute("volunteerCount", result.volunteerCount());
        model.addAttribute("departmentCount", result.departmentCount());
        model.addAttribute("shiftCount", result.shiftCount());
        var upcoming = data.rows();
        model.addAttribute("upcoming", upcoming);
        model.addAttribute("laterShifts", upcoming.stream().skip(1).toList());
        if (!upcoming.isEmpty()) {
            var next = upcoming.getFirst();
            model.addAttribute("nextShift", next);
            model.addAttribute("shiftInProgress", LocalDateTime.of(next.shift().getDate(), next.shift().getStartTime()).compareTo(now) <= 0);
            var contacts = result.contacts();
            model.addAttribute("nextContacts", contacts);
        }
        return "pages/home";
    }
    String more(long eventId, Model model, HttpSession session) {
        model.addAttribute("event", service.event(eventId));
        model.addAttribute("base", "/events/" + eventId);
        session.setAttribute("activeEvent", eventId);
        return "pages/more";
    }
}
