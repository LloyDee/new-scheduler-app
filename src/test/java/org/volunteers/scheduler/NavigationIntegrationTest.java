package org.volunteers.scheduler;

import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.*;
import org.volunteers.scheduler.repository.DeviceVisits;
import org.volunteers.scheduler.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:navigation-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"})
@AutoConfigureMockMvc
@Transactional
class NavigationIntegrationTest {
    @Autowired SchedulerService service;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired DeviceVisits visits;
    Event event;
    Department department;
    Volunteer owner;
    MockHttpSession session;
    @BeforeEach void setup() {
        event = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-04"));
        department = service.saveDepartment(event.getId(), null, Map.of("name", "Attendants"));
        owner = service.saveVolunteer(event.getId(), null, profile("Alex Actual", "2105550000", "OVERSEER"));
        session = new MockHttpSession();
        session.setAttribute(PhoneAccess.SESSION, "2105550000");
        session.setAttribute("activeEvent", event.getId());
    }
    Map<String, String> profile(String name, String phone, String role) {
        return Map.of("name", name, "phone", phone, "congregation", "Central", "privilege", "PUBLISHER",
            "departmentRole_" + department.getId(), role);
    }
    String base() { return "/events/" + event.getId(); }

    @Test void saveReturnsToFilteredVolunteerPage() throws Exception {
        String target = base() + "/volunteers?departmentId=" + department.getId() + "&q=Alex%20%26%20Sam&page=2&scroll=730";
        mvc.perform(post(base() + "/volunteers/save").session(session).with(csrf())
                .param("id", owner.getId().toString()).param("name", "Alex Actual")
                .param("phone", owner.getPhone()).param("congregation", "Central")
                .param("privilege", "PUBLISHER").param("departmentRole_" + department.getId(), "OVERSEER")
                .param("returnTo", target))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(target));
    }

    @Test void failedShiftSaveKeepsContextForRetry() throws Exception {
        String target = base() + "/schedule?departmentId=" + department.getId() + "&date=2027-06-04&page=2&view=needs&scroll=930";
        String url = mvc.perform(post(base() + "/schedule/save").session(session).with(csrf())
                .param("returnTo", target).param("name", ""))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(url).startsWith(target + "&create=true&returnTo=");
        assertThat(java.net.URLDecoder.decode(url.substring(url.indexOf("&returnTo=") + 10), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(target);
    }

    @Test void foreignReturnUrlIsIgnored() throws Exception {
        mvc.perform(post(base() + "/schedule/save").session(session).with(csrf())
                .param("returnTo", "https://example.com/schedule").param("name", ""))
                .andExpect(redirectedUrl(base() + "/schedule?create=true"));
    }

    @Test void volunteerCardsUseMenusAndShortLabelsAndEditorOwnsDelete() throws Exception {
        owner.setCongregation("North   Central Park Congregation, Chicago");
        em.flush();
        String html = mvc.perform(get(base() + "/volunteers").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("View Profile", "Edit Profile", "volunteer-menu-toggle", ">You</span>",
                ">North Central Park</p>")
                .doesNotContain("class=\"card-actions\"", ">Delete</button>", "Add / Move Profile");
        // An overseer can manage ordinary volunteers, including their own profile.
        mvc.perform(get(base() + "/volunteers").session(session).param("edit", owner.getId().toString()))
                .andExpect(status().isOk()).andExpect(model().attribute("canDeleteVolunteer", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/volunteers/" + owner.getId() + "/delete")));
        owner.setEventAdministrator(true);
        em.flush();
        html = mvc.perform(get(base() + "/volunteers").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Add / Move Profile</span>", "/volunteers/transfer").doesNotContain("To Be Implemented");
        owner.getDepartmentRoles().replaceAll((d, role) -> Volunteer.Role.VOLUNTEER);
        owner.setEventAdministrator(false);
        em.flush();
        mvc.perform(get(base() + "/volunteers").session(session).param("edit", owner.getId().toString()))
                .andExpect(status().isOk()).andExpect(model().attribute("canDeleteVolunteer", false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Delete</button>"))));
    }

    @Test void sectionResponsesKeepPermissionsAndOmitSharedShell() throws Exception {
        for (String path : List.of(base() + "/home", base() + "/volunteers", base() + "/schedule",
                base() + "/departments", base() + "/more", "/events", "/logs", "/settings")) {
            String html = mvc.perform(get(path).session(session).header("X-Scheduler-Section", "main"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("id=\"main\"", "data-page-title=", "class=\"bottom-nav\"",
                    "class=\"active-event-name\">Regional Convention</span>", "Switch active event")
                .contains("data-own-volunteer-link", path.endsWith("/volunteers")
                    ? "revealSelf=true#volunteer-name-" + owner.getId() : base() + "/volunteers?view=" + owner.getId())
                .doesNotContain("<html", "<head>", "class=\"app-header\"", "id=\"notification-dialog\"", "<script");
        }
        mvc.perform(get(base() + "/home").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"app-header\"")));
        mvc.perform(get(base() + "/home").header("X-Scheduler-Section", "main"))
            .andExpect(redirectedUrl("/login"));
    }

    @Test void eventSwitcherUsesCircuitCodeAndUpdatesWhenNavigating() throws Exception {
        Event assembly = service.saveEvent(null, Map.of("type", "CIRCUIT_ASSEMBLY",
            "startDate", "2027-06-04", "assembly", "with Circuit Overseer", "circuit", "FL-58A"));
        String html = mvc.perform(get("/events/" + assembly.getId() + "/home").session(session)
                .header("X-Scheduler-Section", "main"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("class=\"active-event-name\">Circuit Assembly FL-58A</span>",
            "<summary class=\"active-event-switcher\"", "class=\"event-picker-list\"",
            "action=\"/events/" + event.getId() + "/select\"",
            "action=\"/events/" + assembly.getId() + "/select\"")
            .doesNotContain("event-switch-label", ">Switch</span>");
        mvc.perform(get("/settings").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "class=\"active-event-name\">Circuit Assembly FL-58A</span>")));
        mvc.perform(post(base() + "/select").session(session).with(csrf()))
            .andExpect(redirectedUrl(base() + "/home"));
        assertThat(session.getAttribute("activeEvent")).isEqualTo(event.getId());
    }

    @Test void volunteerDatabasePagesAreStableFilteredAndSupportOffPageDetails() throws Exception {
        for (int i = 1; i <= 51; i++) service.saveVolunteer(event.getId(), null,
            profile(String.format("Volunteer %03d", i), String.format("210555%04d", i), "VOLUNTEER"));
        em.flush(); em.clear();
        var first = service.volunteerPage(event.getId(), null, "", 0, List.of());
        var second = service.volunteerPage(event.getId(), null, "", 1, List.of());
        assertThat(first.page().getTotalElements()).isEqualTo(52);
        assertThat(first.page().getContent()).hasSize(25);
        assertThat(second.page().getContent()).hasSize(25);
        assertThat(first.page().getContent()).extracting(Volunteer::getId)
            .doesNotContainAnyElementsOf(second.page().getContent().stream().map(Volunteer::getId).toList());
        assertThat(service.volunteerPage(event.getId(), department.getId(), "Volunteer 051", 0, List.of()).page().getContent())
            .extracting(Volunteer::getName).containsExactly("Volunteer 051");
        var last = service.volunteerPage(event.getId(), null, "", 2, List.of());
        assertThat(last.page().getContent()).hasSize(2);
        Long offPage = last.page().getContent().getLast().getId();
        mvc.perform(get(base() + "/volunteers").session(session).param("view", offPage.toString()))
            .andExpect(status().isOk()).andExpect(model().attribute("openVolunteerId", offPage));
        mvc.perform(get(base() + "/volunteers").session(session).param("departmentId", department.getId().toString()).param("q", "Volunteer"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")));
        assertThat(service.volunteerPage(event.getId(), null, "", 1000, List.of()).page().getContent()).isEmpty();
    }

    @Test void schedulePagesKeepConflictsOutsideVisiblePage() throws Exception {
        Shift hidden = null;
        for (int i = 0; i < 26; i++) hidden = service.saveShift(event.getId(), null, Map.of("name", "Shift " + i,
            "departmentId", department.getId().toString(), "date", "2027-06-04", "startTime", "09:00",
            "endTime", "10:00", "requiredCount", "1"));
        service.assign(event.getId(), hidden.getId(), owner.getId(), false);
        var response = mvc.perform(get(base() + "/schedule").session(session)).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked") var rows = (List<SchedulerService.ShiftRow>) response.getModelAndView().getModel().get("rows");
        assertThat(rows).hasSize(25);
        @SuppressWarnings("unchecked") var options = (Map<Long, List<Volunteer>>) response.getModelAndView().getModel().get("assignmentOptions");
        assertThat(options.values()).allSatisfy(eligible -> assertThat(eligible).isEmpty());
        var detail = mvc.perform(get(base() + "/schedule/" + rows.getFirst().shift().getId() + "/options").session(session))
            .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked") var loaded = (Map<Long, List<Volunteer>>) detail.getModelAndView().getModel().get("assignmentOptions");
        assertThat(loaded.get(rows.getFirst().shift().getId())).extracting(Volunteer::getId).doesNotContain(owner.getId());
        mvc.perform(get(base() + "/schedule").session(session).param("page", "1"))
            .andExpect(status().isOk()).andExpect(model().attribute("rows", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test void logNamesComeFromSessionAndSharedDevicesKeepSeparateUsers() throws Exception {
        Cookie device = new Cookie("schedulerDevice", UUID.randomUUID().toString());
        mvc.perform(post("/api/device-visits").session(session).cookie(device).with(csrf())
            .contentType("application/json").content("{\"name\":\"Forged name\",\"browser\":\"Firefox\"}"))
            .andExpect(status().isNoContent());
        assertThat(visits.findAll()).extracting(v -> v.name).containsExactly("Alex Actual");
        service.saveVolunteer(event.getId(), null, profile("Another Person", "2105550999", "VOLUNTEER"));
        var another = new MockHttpSession(); another.setAttribute(PhoneAccess.SESSION, "2105550999");
        mvc.perform(post("/api/device-visits").session(another).cookie(device).with(csrf())
            .contentType("application/json").content("{}"))
            .andExpect(status().isNoContent());
        assertThat(visits.findAll()).extracting(v -> v.name).containsExactlyInAnyOrder("Alex Actual", "Another Person");
        mvc.perform(get("/logs").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Alex Actual")));
    }

    @Test void detailsAreFetchedAsScopedFragmentsWithProtectedAssignmentChoices() throws Exception {
        owner.setNotes("Only in the requested profile");
        var shift = service.saveShift(event.getId(), null, Map.of("name", "Detail shift", "departmentId", department.getId().toString(),
            "date", "2027-06-04", "startTime", "09:00", "endTime", "10:00", "requiredCount", "2"));
        var ids = new ArrayList<Long>();
        ids.add(owner.getId());
        Volunteer member = null;
        for (int i = 0; i < 8; i++) {
            member = service.saveVolunteer(event.getId(), null, profile("Team member " + i, String.format("210555%04d", 100 + i), "VOLUNTEER"));
            ids.add(member.getId());
        }
        service.updateAssignments(event.getId(), shift.getId(), ids);
        em.flush(); em.clear();
        String volunteers = mvc.perform(get(base() + "/volunteers").session(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(volunteers).contains("data-detail-url", "Loading details...").doesNotContain("Only in the requested profile", "volunteer-assignment");
        String schedule = mvc.perform(get(base() + "/schedule").session(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(schedule).contains("/options", "View all 9 volunteers").doesNotContain("assignment-choices", "name=\"volunteerIds\"", "shift-details-list");
        for (String suffix : List.of("/detail", "/team", "/options")) {
            String fragment = mvc.perform(get(base() + "/schedule/" + shift.getId() + suffix).session(session))
                .andExpect(status().isOk()).andExpect(header().string("X-Scheduler-Detail", "true"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
            assertThat(fragment).contains("Team member 7").doesNotContain("<html", "<script", "app-header");
            if (suffix.equals("/options")) assertThat(fragment).contains("name=\"_csrf\"", "checked=\"checked\"", "name=\"volunteerIds\"");
        }
        mvc.perform(get(base() + "/volunteers/" + owner.getId() + "/detail").session(session))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Only in the requested profile")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Detail shift")));
        mvc.perform(get(base() + "/departments/" + department.getId() + "/detail").session(session))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Detail shift")));
        var ordinary = new MockHttpSession(); ordinary.setAttribute(PhoneAccess.SESSION, member.getPhone().replaceAll("[^0-9]", ""));
        mvc.perform(get(base() + "/schedule/" + shift.getId() + "/options").session(ordinary)).andExpect(status().isForbidden());
        mvc.perform(get(base() + "/schedule/" + shift.getId() + "/detail").session(ordinary)).andExpect(status().isOk());
        mvc.perform(get(base() + "/schedule/" + shift.getId() + "/detail")).andExpect(status().is3xxRedirection());
        var other = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-11"));
        String foreign = "/events/" + other.getId();
        for (String path : List.of("/volunteers/" + owner.getId() + "/detail", "/departments/" + department.getId() + "/detail",
                "/schedule/" + shift.getId() + "/detail", "/schedule/" + shift.getId() + "/team")) {
            mvc.perform(get(foreign + path).session(session)).andExpect(status().isNotFound());
        }
    }
}
