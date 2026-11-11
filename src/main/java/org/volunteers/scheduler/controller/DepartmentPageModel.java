package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ui.Model;
import org.volunteers.scheduler.model.Department;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.SchedulerService;

@org.springframework.stereotype.Component
public class DepartmentPageModel extends PageModelSupport {
    public DepartmentPageModel(SchedulerService service, org.volunteers.scheduler.service.PhoneAccess access) {
        super(service, access);
    }
    String departments(long eventId, Long edit,
                       Long view, boolean create,
                       int page,
                       Model model, HttpSession session) {
        var result = service.departmentList(eventId, page, java.util.stream.Stream.of(edit, view).filter(java.util.Objects::nonNull).toList());
        var data = result.snapshot();
        prepare(data, model, session);
        var departmentPage = result.page();
        model.addAttribute("listPage", departmentPage);
        model.addAttribute("departments", departmentPage.getContent());
        Map<String, String> defaults = new HashMap<>();
        if (edit != null) {
            Department d = snapshotDepartment(data, edit);
            defaults.putAll(Map.of("name", d.getName(), "description", d.getDescription()));
        }
        Map<Long, Map<String, Object>> departmentDetails = new HashMap<>();
        Map<Long, List<SchedulerService.ShiftRow>> departmentSchedules = new HashMap<>();
        var rows = data.rows();
        for (Department d : departmentPage.getContent()) {
            departmentSchedules.put(d.getId(), rows.stream().filter(row -> row.shift().getDepartment().getId().equals(d.getId())).toList());
            List<Volunteer> members = data.volunteers().stream()
                    .filter(v -> v.getDepartmentRoles().keySet().stream().anyMatch(dept -> dept.getId().equals(d.getId()))).toList();
            Map<String, Object> details = new HashMap<>();
            details.put("volunteerCount", members.size());
            for (Volunteer.Role role : List.of(Volunteer.Role.OVERSEER, Volunteer.Role.ASSISTANT_OVERSEER)) {
                List<Volunteer> names = members.stream()
                        .filter(v -> v.getDepartmentRoles().entrySet().stream()
                                .anyMatch(entry -> entry.getKey().getId().equals(d.getId()) && entry.getValue() == role))
                        .toList();
                details.put(role.name(), names);
            }
            departmentDetails.put(d.getId(), details);
        }
        model.addAttribute("departmentDetails", departmentDetails);
        model.addAttribute("departmentSchedules", departmentSchedules);
        var dialogDepartments = new ArrayList<>(departmentPage.getContent());
        if (view != null) {
            Department selected = snapshotDepartment(data, view);
            model.addAttribute("selectedDepartment", selected);
            if (dialogDepartments.stream().noneMatch(d -> d.getId().equals(view))) dialogDepartments.add(selected);
            var selectedRows = rows.stream().filter(r -> r.shift().getDepartment().getId().equals(view)).toList();
            departmentSchedules.put(view, selectedRows);
            model.addAttribute("departmentRows", selectedRows);
        }
        model.addAttribute("dialogDepartments", dialogDepartments);
        draft(model, defaults); model.addAttribute("editId", edit);
        model.addAttribute("showEditor", create || edit != null || model.containsAttribute("error") && model.containsAttribute("draft"));
        return Boolean.TRUE.equals(model.getAttribute("showEditor")) ? "pages/departments-editor" : "pages/departments";
    }
}
