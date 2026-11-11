package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.model.Volunteer;

import static org.volunteers.scheduler.controller.WebActions.*;

@Controller
public class VolunteerController {
    private final org.volunteers.scheduler.service.PhoneAccess access;
    private final org.volunteers.scheduler.service.VolunteerImportService imports;
    private final VolunteerPageModel volunteerPageModel;
    public VolunteerController(org.volunteers.scheduler.service.PhoneAccess access, org.volunteers.scheduler.service.VolunteerImportService imports, VolunteerPageModel volunteerPageModel) {
        this.access = access;
        this.imports = imports;
        this.volunteerPageModel = volunteerPageModel;
    }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VolunteerController.class);

    @GetMapping("/events/{eventId}/volunteers")
    String volunteers(@PathVariable long eventId, @RequestParam(defaultValue = "") String q,
                      @RequestParam(required = false) Long departmentId,
                      @RequestParam(required = false) Long edit, @RequestParam(required = false) Long view,
                      @RequestParam(required = false) Long assignments,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "false") boolean create,
                      Model model, HttpSession session, HttpServletRequest request) {
        return volunteerPageModel.volunteers(eventId, q, departmentId, edit, view, assignments, page, create, model, session, request);
    }

    @PostMapping("/events/{eventId}/volunteers/save")
    String saveVolunteer(@PathVariable long eventId, @RequestParam(required = false) Long id,
                         @RequestParam Map<String, String> input, RedirectAttributes flash, HttpSession session) {
        String base = "/events/" + eventId + "/volunteers";
        return attempt(() -> { if (access.canManage(session, eventId)) access.saveManagedVolunteer(eventId, id, input, session);
            else access.saveOwn(eventId, id, input, session); return base; },
                base + editQuery(id), input, flash, "Volunteer saved.");
    }

    @PostMapping("/events/{eventId}/volunteers/{id}/delete")
    String deleteVolunteer(@PathVariable long eventId, @PathVariable long id, RedirectAttributes flash, HttpSession session) {
        try { access.deleteManagedVolunteer(eventId, id, session); flash.addFlashAttribute("notice", "Volunteer and their assignments deleted."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/events/" + eventId + "/volunteers";
    }

    @GetMapping("/settings/data/volunteers/template.csv")
    ResponseEntity<String> volunteerTemplate(HttpSession session) {
        Long eventId = (Long) session.getAttribute("activeEvent");
        if (eventId == null) throw new IllegalArgumentException("Choose an active event first.");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=volunteers.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(imports.template(eventId));
    }

    @PostMapping("/settings/data/volunteers/import")
    String importVolunteers(@RequestParam("file") MultipartFile file, HttpSession session, RedirectAttributes flash) {
        Long eventId = (Long) session.getAttribute("activeEvent");
        try {
            if (eventId == null) throw new IllegalArgumentException("Choose an active event first.");
            int count = imports.importFile(eventId, file);
            flash.addFlashAttribute("importNotice", count + (count == 1 ? " volunteer imported." : " volunteers imported."));
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("importError", ex.getMessage() + " No volunteers were imported.");
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.error("Volunteer import rejected by the database for event {}", eventId, ex);
            flash.addFlashAttribute("importError", "Import failed because the database rejected a record. No volunteers were imported. Contact the administrator.");
        }
        return "redirect:/settings#data";
    }
}
