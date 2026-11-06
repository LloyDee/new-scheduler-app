package org.volunteers.scheduler.service;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.volunteers.scheduler.model.*;
import org.volunteers.scheduler.repository.*;

@Service
@Transactional
public class AnnouncementService {
    private final Announcements announcements;
    private final AnnouncementRecipients recipients;
    private final Departments departments;
    private final Volunteers volunteers;
    private final PhoneAccess access;
    public AnnouncementService(Announcements announcements, AnnouncementRecipients recipients, Departments departments,
                               Volunteers volunteers, PhoneAccess access) {
        this.announcements = announcements; this.recipients = recipients; this.departments = departments;
        this.volunteers = volunteers; this.access = access;
    }
    public record Message(Long id, String scope, Long eventId, String eventName, String departmentName,
                          String title, String message, Instant createdAt, boolean acknowledgmentRequired,
                          Instant readAt, Instant acknowledgedAt) {}
    public record Progress(Announcement announcement, long total, long acknowledged, List<String> acknowledgedFirstNames) {}
    public List<Department> allowed(long eventId, HttpSession session) {
        return departments.findByEventIdOrderByNameAscIdAsc(eventId).stream()
            .filter(d -> access.canAnnounce(session, eventId, d.getId())).toList();
    }
    private Department authorized(long eventId, long departmentId, HttpSession session) {
        Department d = departments.findById(departmentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!d.getEvent().getId().equals(eventId) || !access.canAnnounce(session, eventId, departmentId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return d;
    }
    private List<Volunteer> audience(long eventId, long departmentId) {
        return volunteers.findByEventIdOrderByNameAscIdAsc(eventId).stream()
            .filter(v -> v.getDepartmentRoles().keySet().stream().anyMatch(d -> d.getId().equals(departmentId))).toList();
    }
    public int preview(long eventId, long departmentId, HttpSession session) {
        authorized(eventId, departmentId, session);
        return audience(eventId, departmentId).size();
    }
    public void send(long eventId, long departmentId, String title, String message, boolean required, HttpSession session) {
        Department d = authorized(eventId, departmentId, session);
        if (title == null || title.isBlank() || title.strip().length() > 160 || message == null || message.isBlank() || message.strip().length() > 5000)
            throw new IllegalArgumentException("Enter a title up to 160 characters and a message up to 5,000 characters.");
        var audience = audience(eventId, departmentId);
        if (audience.isEmpty()) throw new IllegalArgumentException("This department has no volunteers to receive an announcement.");
        Announcement a = new Announcement();
        a.eventId = eventId; a.departmentId = departmentId; a.departmentName = d.getName(); a.eventName = d.getEvent().getName();
        a.title = title.strip(); a.message = message.strip(); a.acknowledgmentRequired = required;
        a.sender = access.isAppOwner(session) ? "App owner" : access.displayName(session); a.createdAt = Instant.now();
        announcements.save(a);
        for (Volunteer v : audience) {
            AnnouncementRecipient r = new AnnouncementRecipient(); r.announcementId = a.id; r.volunteerId = v.getId(); recipients.save(r);
        }
    }
    public List<Progress> history(long eventId, HttpSession session) {
        return announcements.findByEventIdOrderByCreatedAtDescIdDesc(eventId).stream()
            .filter(a -> access.canAnnounce(session, eventId, a.departmentId)).map(a -> {
                var rows = recipients.findByAnnouncementId(a.id);
                var acknowledged = rows.stream().filter(r -> r.acknowledgedAt != null).toList();
                Map<Long, String> names = new HashMap<>();
                if (!acknowledged.isEmpty()) {
                    volunteers.findAllById(acknowledged.stream().map(r -> r.volunteerId).toList())
                        .forEach(v -> names.put(v.getId(), v.getName().strip().split("\\s+", 2)[0]));
                }
                var firstNames = acknowledged.stream().map(r -> names.getOrDefault(r.volunteerId, "Unknown user"))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
                return new Progress(a, rows.size(), acknowledged.size(), firstNames);
            }).toList();
    }
    public List<Message> pending(Long eventId, HttpSession session) {
        Set<Long> own = access.ids(session);
        if (eventId == null || own.isEmpty()) return List.of();
        Map<Long, Announcement> messages = new HashMap<>();
        announcements.findByEventIdOrderByCreatedAtDescIdDesc(eventId).forEach(a -> messages.put(a.id, a));
        return recipients.findByVolunteerIdIn(own).stream().filter(r -> messages.containsKey(r.announcementId))
            .filter(r -> r.readAt == null || (messages.get(r.announcementId).acknowledgmentRequired && r.acknowledgedAt == null))
            .map(r -> { var a = messages.get(r.announcementId); return new Message(a.id, "DEPARTMENT", a.eventId, a.eventName,
                a.departmentName, a.title, a.message, a.createdAt, a.acknowledgmentRequired, r.readAt, r.acknowledgedAt); })
            .sorted(Comparator.comparing(Message::createdAt).reversed()).toList();
    }
    public void mark(long id, HttpSession session, boolean acknowledge) {
        var a = announcements.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var own = access.ids(session);
        var rows = recipients.findByAnnouncementId(id).stream().filter(r -> own.contains(r.volunteerId)).toList();
        if (a.eventId == null || rows.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (acknowledge && !a.acknowledgmentRequired) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Instant now = Instant.now();
        for (var r : rows) {
            if (r.readAt == null) r.readAt = now;
            if (acknowledge && r.acknowledgedAt == null) r.acknowledgedAt = now;
        }
    }
}
