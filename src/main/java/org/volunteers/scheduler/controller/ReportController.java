package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.service.BackupService;
import org.volunteers.scheduler.service.SchedulerService;

import static org.volunteers.scheduler.controller.WebActions.*;

@Controller
public class ReportController {
    private final SchedulerService service;
    private final BackupService backups;
    private final org.volunteers.scheduler.service.ReportService reports;
    private final ReportPageModel reportPageModel;
    public ReportController(SchedulerService service, BackupService backups, org.volunteers.scheduler.service.ReportService reports, ReportPageModel reportPageModel) {
        this.service = service;
        this.backups = backups;
        this.reports = reports;
        this.reportPageModel = reportPageModel;
    }
    @GetMapping("/events/{eventId}/reports")
    String reports(@PathVariable long eventId, @RequestParam(defaultValue = "department") String type,
                   @RequestParam(required = false) Long departmentId, @RequestParam(required = false) LocalDate date,
                   Model model, HttpSession session) {
        return reportPageModel.reports(eventId, type, departmentId, date, model, session);
    }

    @GetMapping("/events/{eventId}/reports.csv")
    ResponseEntity<byte[]> csv(@PathVariable long eventId, @RequestParam(defaultValue = "department") String type,
                               @RequestParam(required = false) Long departmentId, @RequestParam(required = false) LocalDate date,
                               @RequestParam(required = false) String snapshot, HttpSession session) {
        return reports.csv(eventId, type, departmentId, date, snapshot, session);
    }

    @PostMapping("/events/{eventId}/backups/refresh")
    String refreshBackup(@PathVariable long eventId, HttpSession session, RedirectAttributes flash) {
        synchronized (session) { session.setAttribute("csvBackup", backups.capture(eventId)); }
        flash.addFlashAttribute("notice", "New backup captured. Download all three CSV files from this snapshot.");
        return "redirect:/events/" + eventId + "/reports?type=backup";
    }

    @PostMapping("/events/{eventId}/backups/restore")
    String restoreBackup(@PathVariable long eventId, @RequestParam(required = false) MultipartFile departments,
                         @RequestParam(required = false) MultipartFile volunteers, @RequestParam(required = false) MultipartFile shifts,
                         @RequestParam(defaultValue = "false") boolean confirmReplace,
                         HttpSession session, RedirectAttributes flash) {
        service.event(eventId);
        try {
            if (!confirmReplace) throw new IllegalArgumentException("Confirm replacement of this event's data before restoring.");
            if (departments == null || volunteers == null || shifts == null)
                throw new IllegalArgumentException("Choose all three backup CSV files.");
            Map<String, byte[]> files = new HashMap<>();
            for (var entry : Map.of("departments", departments, "volunteers", volunteers, "shifts", shifts).entrySet()) {
                var file = entry.getValue();
                if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    throw new IllegalArgumentException(entry.getKey() + ": Choose a backup CSV file.");
                files.put(entry.getKey(), file.getBytes());
            }
            var counts = backups.restore(eventId, files);
            synchronized (session) { session.removeAttribute("csvBackup"); }
            flash.addFlashAttribute("notice", "Backup restored: " + counts.get("departments") + " departments, "
                    + counts.get("volunteers") + " volunteers, " + counts.get("shifts") + " shifts, " + counts.get("assignments") + " assignments.");
        } catch (IllegalArgumentException | java.io.IOException ex) {
            flash.addFlashAttribute("error", "Restore failed. Existing data was kept. " + ex.getMessage());
        } catch (org.springframework.dao.DataAccessException ex) {
            flash.addFlashAttribute("error", "Restore failed because the data could not be saved. Existing data was kept.");
        }
        return "redirect:/events/" + eventId + "/reports?type=backup";
    }
}
