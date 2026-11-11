package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import org.springframework.ui.Model;
import org.volunteers.scheduler.service.BackupService;
import org.volunteers.scheduler.service.ReportService;
import org.volunteers.scheduler.service.SchedulerService;

@org.springframework.stereotype.Component
public class ReportPageModel extends PageModelSupport {
    private final ReportService reports;
    public ReportPageModel(SchedulerService service, org.volunteers.scheduler.service.PhoneAccess access, ReportService reports) {
        super(service, access); this.reports = reports;
    }
    String reports(long eventId, String type,
                   Long departmentId, LocalDate date,
                   Model model, HttpSession session) {
        var data = prepare(eventId, model, session);
        boolean backupView = "backup".equals(type) || BackupService.supports(type);
        model.addAttribute("backup", backupView);
        if (backupView) {
            var backup = reports.backup(eventId, session);
            model.addAttribute("backupCounts", backup.counts());
            model.addAttribute("backupToken", backup.token());
            departmentId = null; date = null;
        }
        var rows = filter(data, departmentId, date, model);
        model.addAttribute("reportType", backupView ? "backup" : ReportService.reportType(type));
        model.addAttribute("volunteerRows", ReportService.volunteerReport(data, rows, departmentId != null || date != null));
        return "pages/reports";
    }
}
