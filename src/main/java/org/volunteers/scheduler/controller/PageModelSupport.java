package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.SchedulerService;

/** Shared snapshot and form helpers for feature page models. */
public abstract class PageModelSupport {
    protected final SchedulerService service;
    protected final org.volunteers.scheduler.service.PhoneAccess access;
    protected PageModelSupport(SchedulerService service, org.volunteers.scheduler.service.PhoneAccess access) {
        this.service = service; this.access = access;
    }
    protected static Volunteer snapshotVolunteer(SchedulerService.Snapshot data, long id) {
        return data.volunteers().stream().filter(v -> v.getId().equals(id)).findFirst()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    protected static Department snapshotDepartment(SchedulerService.Snapshot data, long id) {
        return data.departments().stream().filter(d -> d.getId().equals(id)).findFirst()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    protected SchedulerService.Snapshot prepare(long eventId, Model model, HttpSession session) {
        var data = service.snapshot(eventId);
        prepare(data, model, session);
        return data;
    }

    protected void prepare(SchedulerService.Snapshot data, Model model, HttpSession session) {
        long eventId = data.event().getId(); session.setAttribute("activeEvent", eventId);
        model.addAttribute("event", data.event()); model.addAttribute("base", "/events/" + eventId);
        model.addAttribute("volunteers", data.volunteers()); model.addAttribute("departments", data.departments());
        model.addAttribute("allRows", data.rows());
    }

    protected static <T> org.springframework.data.domain.Page<T> page(List<T> items, int number) {
        int safePage = Math.max(0, number);
        int start = (int) Math.min((long) safePage * 25, items.size());
        return new org.springframework.data.domain.PageImpl<>(items.subList(start, Math.min(start + 25, items.size())),
            org.springframework.data.domain.PageRequest.of(safePage, 25), items.size());
    }

    protected List<SchedulerService.ShiftRow> filter(SchedulerService.Snapshot data, Long departmentId, LocalDate date, Model model) {
        var rows = filtered(data, departmentId, date);
        model.addAttribute("rows", rows); model.addAttribute("departmentId", departmentId); model.addAttribute("date", date); return rows;
    }

    protected List<SchedulerService.ShiftRow> filtered(SchedulerService.Snapshot data, Long departmentId, LocalDate date) {
        return org.volunteers.scheduler.service.ReportService.filtered(data, departmentId, date);
    }

    @SuppressWarnings("unchecked")
    protected static void draft(Model model, Map<String, String> defaults) {
        Object previous = model.getAttribute("draft");
        if (previous instanceof Map<?, ?> map) defaults.putAll((Map<String, String>) map);
        model.addAttribute("draft", defaults);
    }
}
