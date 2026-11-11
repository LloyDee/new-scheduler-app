package org.volunteers.scheduler.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.Event;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.repository.Assignments;
import org.volunteers.scheduler.repository.Departments;
import org.volunteers.scheduler.repository.Events;
import org.volunteers.scheduler.repository.Shifts;
import org.volunteers.scheduler.repository.Volunteers;

import static org.volunteers.scheduler.service.SchedulerService.*;

@Service
@Transactional(readOnly = true)
public class EventService {
    private final Events events;
    private final Volunteers volunteers;
    private final Departments departments;
    private final Shifts shifts;
    private final Assignments assignments;
    private final org.volunteers.scheduler.repository.Announcements announcements;
    private final org.volunteers.scheduler.repository.AnnouncementRecipients announcementRecipients;
    private final OwnerIdentity ownerIdentity;
    private final SchedulerQueries schedulerQueries;
    @org.springframework.beans.factory.annotation.Value("${scheduler.app-owner-phone:8135160335}")
    private String ownerPhone;

    public EventService(Events events, Volunteers volunteers, Departments departments, Shifts shifts, Assignments assignments, org.volunteers.scheduler.repository.Announcements announcements, org.volunteers.scheduler.repository.AnnouncementRecipients announcementRecipients, OwnerIdentity ownerIdentity, SchedulerQueries schedulerQueries) {
        this.events = events;
        this.volunteers = volunteers;
        this.departments = departments;
        this.shifts = shifts;
        this.assignments = assignments;
        this.announcements = announcements;
        this.announcementRecipients = announcementRecipients;
        this.ownerIdentity = ownerIdentity;
        this.schedulerQueries = schedulerQueries;
    }

    @Transactional
    public Volunteer ensureAdministrator(Event event, String phone) {
        if (ownerIdentity.matches(phone)) return null;
        String key = PhoneAccess.normalize(phone);
        require(!key.isEmpty(), "Configure a valid App Owner phone number.");
        Volunteer member = volunteers.findByEventIdOrderByNameAscIdAsc(event.getId()).stream()
            .filter(v -> key.equals(PhoneAccess.normalize(v.getPhone()))).findFirst().orElse(null);
        if (member == null) {
            var profile = volunteers.findByNormalizedPhone(key).stream().findFirst().orElse(null);
            member = new Volunteer(); member.setEvent(event); member.setPhone(key);
            member.setName(profile == null ? "App Owner" : profile.getName());
            member.setEmail(profile == null ? "" : profile.getEmail());
            member.setCongregation(profile == null ? "" : profile.getCongregation());
            member.setNotes(""); member.setPrivilege(profile == null ? Volunteer.Privilege.PUBLISHER : profile.getPrivilege());
        }
        member.setEventAdministrator(true);
        return volunteers.save(member);
    }

    public List<Event> events() { return events.findAllByOrderByStartDateDescIdDesc(); }

    public org.springframework.data.domain.Page<Event> eventsPage(int page) {
        return events.findAllByOrderByStartDateDescIdDesc(org.springframework.data.domain.PageRequest.of(Math.max(0, page), 25));
    }

    @Transactional
    public Event saveEvent(Long id, Map<String, String> input) {
        return saveEvent(id, input, ownerPhone);
    }

    @Transactional
    public Event saveEvent(Long id, Map<String, String> input, String administratorPhone) {
        Event.Type type;
        try { type = Event.Type.valueOf(input.getOrDefault("type", "")); }
        catch (IllegalArgumentException ex) { throw invalid("Choose an event type."); }
        LocalDate start = date(input, "startDate");
        LocalDate end = type == Event.Type.REGIONAL_CONVENTION ? start.plusDays(2) : start;
        String assembly = "";
        String name;
        if (type == Event.Type.REGIONAL_CONVENTION) {
            int year = start.getYear() + (start.getMonthValue() >= 9 ? 1 : 0);
            name = year + " Regional Convention";
        } else {
            assembly = required(input, "assembly", 60);
            require(Set.of("with Circuit Overseer", "with Branch Representative").contains(assembly),
                    "Choose a Circuit Assembly representative.");
            name = "Circuit Assembly " + assembly;
        }
        String venue = optional(input, "venue", 240);
        String circuit = type == Event.Type.CIRCUIT_ASSEMBLY ? optional(input, "circuit", 60) : "";
        Event e = id == null ? new Event() : schedulerQueries.event(id);
        if (id != null) {
            require(shifts.findByDepartmentEventIdOrderByDateAscStartTimeAscIdAsc(id).stream()
                    .noneMatch(s -> s.getDate().isBefore(start) || s.getDate().isAfter(end)),
                    "These dates exclude existing shifts. Move or delete those shifts first.");
        }
        e.setName(name); e.setType(type); e.setStartDate(start); e.setEndDate(end); e.setVenue(venue);
        e.setAssembly(assembly);
        e.setCircuit(circuit);
        Event saved = events.save(e);
        if (id == null) ensureAdministrator(saved, administratorPhone);
        return saved;
    }

    @Transactional
    public void deleteEvent(long id) {
        Event e = events.lockForVolunteerUpdate(id).orElseThrow(SchedulerService::notFound);
        for (var announcement : announcements.findByEventIdOrderByCreatedAtDescIdDesc(id)) {
            announcementRecipients.deleteByAnnouncementId(announcement.id);
            announcements.delete(announcement);
        }
        assignments.deleteAll(assignments.findByShiftDepartmentEventIdOrderByIdAsc(id)); assignments.flush();
        shifts.deleteAll(shifts.findByDepartmentEventIdOrderByDateAscStartTimeAscIdAsc(id)); shifts.flush();
        volunteers.deleteAll(volunteers.findByEventIdOrderByNameAscIdAsc(id));
        volunteers.flush();
        departments.deleteAll(departments.findByEventIdOrderByNameAscIdAsc(id));
        volunteers.flush(); departments.flush(); events.delete(e);
    }
}
