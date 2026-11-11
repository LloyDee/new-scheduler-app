package org.volunteers.scheduler.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.service.SchedulerService;

final class WebActions {
    private WebActions() { }
    static String editQuery(Long id) { return id == null ? "?create=true" : "?edit=" + id; }

    static String attempt(Supplier<String> work, String failureUrl, Map<String, String> input,
                                  RedirectAttributes flash, String notice) {
        try { String target = work.get(); flash.addFlashAttribute("notice", notice); return "redirect:" + target; }
        catch (IllegalArgumentException ex) {
            Map<String, String> draft = new HashMap<>(input); draft.remove("_csrf"); draft.remove("confirmOverlap");
            flash.addFlashAttribute("draft", draft); flash.addFlashAttribute("error", ex.getMessage());
            if (ex instanceof SchedulerService.OverlapException) flash.addFlashAttribute("overlap", true);
            return "redirect:" + failureUrl;
        }
    }
}
