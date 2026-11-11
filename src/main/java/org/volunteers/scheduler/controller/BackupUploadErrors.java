package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Multipart parsing can fail before Spring selects a controller, so this handler is global.
@ControllerAdvice
class BackupUploadErrors {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    String backupTooLarge(HttpServletRequest request, RedirectAttributes flash) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/settings/data/volunteers/import")) {
            flash.addFlashAttribute("importError", "File exceeds the 10 MB upload limit. No volunteers were imported.");
            return "redirect:/settings#data";
        }
        flash.addFlashAttribute("error", "Restore failed. Backup files exceed the upload size limit. Existing data was kept.");
        if (path.matches("/events/[0-9]+/backups/restore"))
            return "redirect:" + path.replace("/backups/restore", "/reports?type=backup");
        return "redirect:/";
    }
}
