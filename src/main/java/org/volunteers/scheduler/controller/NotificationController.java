package org.volunteers.scheduler.controller;

import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.model.*;
import org.volunteers.scheduler.repository.*;

@Controller
public class NotificationController {
    private final Announcements announcements;
    private final AnnouncementReads reads;
    private final org.volunteers.scheduler.service.AnnouncementService service;
    private final org.volunteers.scheduler.service.PhoneAccess access;

    public NotificationController(Announcements announcements, AnnouncementReads reads, org.volunteers.scheduler.service.AnnouncementService service, org.volunteers.scheduler.service.PhoneAccess access) {
        this.announcements = announcements; this.reads = reads;
        this.service = service; this.access = access;
    }

    @GetMapping("/api/notifications/count") @ResponseBody
    ResponseEntity<Map<String, Long>> count(@RequestAttribute("schedulerDeviceId") String device, HttpSession session, @RequestParam(required = false) Long eventId) {
        String phone = org.volunteers.scheduler.service.PhoneAccess.normalize((String) session.getAttribute(org.volunteers.scheduler.service.PhoneAccess.SESSION));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("count", announcements.unreadCountFor(device, eventId != null ? eventId : (Long) session.getAttribute("activeEvent"), phone)));
    }

    @GetMapping("/api/notifications") @ResponseBody
    ResponseEntity<List<org.volunteers.scheduler.service.AnnouncementService.Message>> unread(@RequestAttribute("schedulerDeviceId") String device, HttpSession session, @RequestParam(required = false) Long eventId) {
        var result = new ArrayList<>(service.pending(eventId != null ? eventId : (Long) session.getAttribute("activeEvent"), session));
        for (var a : announcements.unread(device)) result.add(new org.volunteers.scheduler.service.AnnouncementService.Message(
            a.id, "GLOBAL", null, null, null, a.title, a.message, a.createdAt, false, null, null));
        result.sort(Comparator.comparing(org.volunteers.scheduler.service.AnnouncementService.Message::createdAt).reversed());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/api/notifications/read") @ResponseBody
    ResponseEntity<Void> read(@RequestAttribute("schedulerDeviceId") String device, @RequestBody List<Long> ids, HttpSession session) {
        for (Long id : new HashSet<>(ids)) {
            if (id == null || !announcements.existsById(id)) continue;
            if (announcements.findById(id).orElseThrow().eventId != null) { service.mark(id, session, false); continue; }
            AnnouncementRead receipt = new AnnouncementRead();
            receipt.deviceId = device; receipt.announcementId = id;
            reads.save(receipt);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/api/notifications/{id}/acknowledge") @ResponseBody
    ResponseEntity<Void> acknowledge(@PathVariable long id, HttpSession session) {
        service.mark(id, session, true);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/events/{eventId}/announcements/broadcast")
    String send(@PathVariable long eventId, @RequestParam String message, HttpServletResponse response, RedirectAttributes flash, HttpSession session) {
        if (!access.isAppOwner(session)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        response.setHeader("Cache-Control", "no-store");
        if (message.isBlank() || message.length() > 5000) {
            flash.addFlashAttribute("notificationError", "Enter a message between 1 and 5,000 characters.");
            flash.addFlashAttribute("notificationDraft", message);
        } else {
            Announcement announcement = new Announcement();
            announcement.message = message.strip(); announcement.createdAt = Instant.now();
            announcements.save(announcement);
            flash.addFlashAttribute("notice", "Notification sent.");
        }
        return "redirect:/events/" + eventId + "/announcements#broadcast";
    }
}
