package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ui.Model;
import org.volunteers.scheduler.model.Volunteer;
import org.volunteers.scheduler.service.SchedulerService;

@org.springframework.stereotype.Component
public class VolunteerPageModel extends PageModelSupport {
    public VolunteerPageModel(SchedulerService service, org.volunteers.scheduler.service.PhoneAccess access) {
        super(service, access);
    }
    String volunteers(long eventId, String q,
                      Long departmentId,
                      Long edit, Long view,
                      Long assignments,
                      int page,
                      boolean create,
                      Model model, HttpSession session, HttpServletRequest request) {
        final Long selectedDepartment = request.getParameter("departmentId") == null ? access.department(session, eventId) : departmentId;
        Long ownId = access.ownVolunteerId(session, eventId);
        boolean revealSelf = Boolean.parseBoolean(request.getParameter("revealSelf"));
        var details = java.util.stream.Stream.of(edit, view, assignments, revealSelf ? ownId : null).filter(Objects::nonNull).toList();
        var result = service.volunteerList(eventId, selectedDepartment, q, page, details, assignments != null);
        var data = result.snapshot();
        prepare(data, model, session);
        model.addAttribute("listPage", result.page());
        model.addAttribute("q", q);
        model.addAttribute("departmentId", selectedDepartment);
        var visibleVolunteers = new ArrayList<>(result.page().getContent());
        if (revealSelf && ownId != null && visibleVolunteers.stream().noneMatch(v -> v.getId().equals(ownId))) {
            visibleVolunteers.add(snapshotVolunteer(data, ownId));
            model.addAttribute("revealedVolunteerId", ownId);
        }
        model.addAttribute("filteredVolunteers", visibleVolunteers);
        Map<Long, String> congregationLabels = new HashMap<>();
        visibleVolunteers.forEach(v -> congregationLabels.put(v.getId(), v.getCongregation() == null ? "" :
                java.util.Arrays.stream(v.getCongregation().strip().split("\\s+")).limit(3)
                        .collect(java.util.stream.Collectors.joining(" "))));
        model.addAttribute("congregationLabels", congregationLabels);
        model.addAttribute("canDeleteVolunteer", false);
        Map<String, String> defaults = new HashMap<>();
        if (edit != null) {
            Volunteer v = snapshotVolunteer(data, edit);
            model.addAttribute("canDeleteVolunteer", access.canManage(session, eventId)
                    && (!v.isEventAdministrator() || access.canAdminister(session, eventId)));
            defaults.putAll(Map.of("name", v.getName(), "phone", v.getPhone(), "email", v.getEmail(),
                    "congregation", v.getCongregation(), "notes", v.getNotes()));
            if (v.isEventAdministrator() && v.getDepartmentRoles().isEmpty()) defaults.put("departmentChoice", "none");
            defaults.put("eventAdministrator", Boolean.toString(v.isEventAdministrator()));
            defaults.put("privilege", v.getPrivilege() == null ? "" : v.getPrivilege().name());
            v.getDepartmentRoles().forEach((department, role) -> defaults.put("departmentRole_" + department.getId(), role.name()));
        }
        Map<Long, Volunteer> dialogVolunteers = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Volunteer> visible = (List<Volunteer>) model.getAttribute("filteredVolunteers");
        assert visible != null;
        visible.forEach(v -> dialogVolunteers.put(v.getId(), v));
        if (view != null) dialogVolunteers.put(view, snapshotVolunteer(data, view));
        model.addAttribute("dialogVolunteers", dialogVolunteers.values());
        model.addAttribute("openVolunteerId", view);
        model.addAttribute("currentVolunteerId", access.ownVolunteerId(session, eventId));
        Map<Long, List<SchedulerService.ShiftRow>> volunteerAssignments = new HashMap<>();
        var rows = data.rows();
        dialogVolunteers.keySet().forEach(id -> volunteerAssignments.put(id, new ArrayList<>()));
        rows.forEach(row -> row.assignments().stream().map(a -> a.getVolunteer().getId()).distinct()
                .filter(volunteerAssignments::containsKey).forEach(id -> volunteerAssignments.get(id).add(row)));
        model.addAttribute("volunteerAssignments", volunteerAssignments);
        if (assignments != null) {
            model.addAttribute("assignmentVolunteer", snapshotVolunteer(data, assignments));
            model.addAttribute("personalRows", data.rows().stream()
                    .filter(row -> row.assignments().stream().anyMatch(a -> a.getVolunteer().getId().equals(assignments))).toList());
        }
        draft(model, defaults); model.addAttribute("editId", edit);
        model.addAttribute("showEditor", create || edit != null || model.containsAttribute("error") && model.containsAttribute("draft"));
        model.addAttribute("volunteerRoles", java.util.Arrays.stream(Volunteer.Role.values()).filter(role -> role != Volunteer.Role.ADMIN).toList());
        model.addAttribute("volunteerPrivileges", Volunteer.Privilege.values());
        return Boolean.TRUE.equals(model.getAttribute("showEditor")) ? "pages/volunteers-editor" : "pages/volunteers";
    }
}
