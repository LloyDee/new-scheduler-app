package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.service.DepartmentService;

import static org.volunteers.scheduler.controller.WebActions.*;

@Controller
public class DepartmentController {
    private final DepartmentService service;
    private final DepartmentPageModel departmentPageModel;
    public DepartmentController(DepartmentService service, DepartmentPageModel departmentPageModel) {
        this.service = service;
        this.departmentPageModel = departmentPageModel;
    }
    @GetMapping("/events/{eventId}/departments")
    String departments(@PathVariable long eventId, @RequestParam(required = false) Long edit,
                       @RequestParam(required = false) Long view, @RequestParam(defaultValue = "false") boolean create,
                       @RequestParam(defaultValue = "0") int page,
                       Model model, HttpSession session) {
        return departmentPageModel.departments(eventId, edit, view, create, page, model, session);
    }

    @PostMapping("/events/{eventId}/departments/save")
    String saveDepartment(@PathVariable long eventId, @RequestParam(required = false) Long id,
                          @RequestParam Map<String, String> input, RedirectAttributes flash) {
        String base = "/events/" + eventId + "/departments";
        return attempt(() -> { service.saveDepartment(eventId, id, input); return base; },
                base + editQuery(id), input, flash, "Department saved.");
    }

    @PostMapping("/events/{eventId}/departments/{id}/delete")
    String deleteDepartment(@PathVariable long eventId, @PathVariable long id, RedirectAttributes flash) {
        service.deleteDepartment(eventId, id); flash.addFlashAttribute("notice", "Department, shifts, and assignments deleted.");
        return "redirect:/events/" + eventId + "/departments";
    }
}
