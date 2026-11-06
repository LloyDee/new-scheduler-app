package org.volunteers.scheduler;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.model.*;
import org.volunteers.scheduler.repository.*;
import org.volunteers.scheduler.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:announcement-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class AnnouncementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SchedulerService scheduler;
    @Autowired AnnouncementService service;
    @Autowired PhoneAccess access;
    @Autowired Announcements announcements;
    @Autowired AnnouncementRecipients recipients;
    @Autowired Volunteers volunteers;
    Event event; Department parking; Department other;
    Volunteer leader; Volunteer member; Volunteer outsider;
    @BeforeEach void setup() {
        event = scheduler.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-04"));
        parking = scheduler.saveDepartment(event.getId(), null, Map.of("name", "Parking"));
        other = scheduler.saveDepartment(event.getId(), null, Map.of("name", "Other"));
        leader = volunteer("2105550101", parking, "OVERSEER");
        member = volunteer("2105550102", parking, "VOLUNTEER");
        outsider = volunteer("2105550103", other, "VOLUNTEER");
    }
    Volunteer volunteer(String phone, Department department, String role) {
        return scheduler.saveVolunteer(event.getId(), null, Map.of("name", phone, "phone", phone,
            "congregation", "Central", "privilege", "PUBLISHER", "departmentRole_" + department.getId(), role));
    }
    MockHttpSession session(Volunteer v) {
        var s = new MockHttpSession(); s.setAttribute(PhoneAccess.SESSION, PhoneAccess.normalize(v.getPhone()));
        s.setAttribute("activeEvent", event.getId()); return s;
    }
    long send(boolean required) {
        service.send(event.getId(), parking.getId(), "Friday arrival", "Arrive at 6:30 AM.", required, session(leader));
        return announcements.findByEventIdOrderByCreatedAtDescIdDesc(event.getId()).getFirst().id;
    }
    @Test void fixedRecipientsAndAcknowledgmentSurviveMembershipChangesAndDevices() throws Exception {
        long id = send(true);
        mvc.perform(get("/events/" + event.getId() + "/announcements").session(session(leader)))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("0 of 2 acknowledged")));
        member.getDepartmentRoles().clear(); volunteers.flush();
        var newcomer = volunteer("2105550104", parking, "VOLUNTEER");
        assertThat(service.pending(event.getId(), session(newcomer))).isEmpty();
        assertThat(service.history(event.getId(), session(leader)).getFirst().total()).isEqualTo(2);
        mvc.perform(get("/api/notifications").session(session(outsider))).andExpect(content().json("[]"));
        mvc.perform(get("/api/notifications/count").session(session(member))).andExpect(jsonPath("$.count").value(1));
        mvc.perform(post("/api/notifications/read").session(session(member)).with(csrf()).contentType("application/json").content("[" + id + "]"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/count").session(session(member))).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/notifications").session(session(member))).andExpect(jsonPath("$[0].acknowledgmentRequired").value(true));
        for (int i=0;i<2;i++) mvc.perform(post("/api/notifications/" + id + "/acknowledge").session(session(member)).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").session(session(member))).andExpect(content().json("[]"));
        assertThat(service.history(event.getId(), session(leader)).getFirst().acknowledged()).isEqualTo(1);
        mvc.perform(get("/api/notifications").param("eventId", "999999").session(session(leader))).andExpect(content().json("[]"));
        mvc.perform(get("/api/notifications/count").param("eventId", "999999").session(session(leader))).andExpect(jsonPath("$.count").value(0));
    }
    @Test void historyDisplaysFirstNamesOnlyAfterAcknowledgment() throws Exception {
        leader.setName("Mary Jones"); member.setName("  John\tSmith  ");
        long id = send(true);
        String path = "/events/" + event.getId() + "/announcements";
        service.mark(id, session(member), false);
        assertThat(service.history(event.getId(), session(leader)).getFirst().acknowledgedFirstNames()).isEmpty();
        mvc.perform(get(path).session(session(leader)))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Acknowledged by:"))));
        service.mark(id, session(member), true);
        service.mark(id, session(member), true);
        member.getDepartmentRoles().clear();
        service.mark(id, session(leader), true);
        var progress = service.history(event.getId(), session(leader)).getFirst();
        assertThat(progress.acknowledged()).isEqualTo(2);
        assertThat(progress.acknowledgedFirstNames()).containsExactly("John", "Mary");
        mvc.perform(get(path).session(session(leader)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("2 of 2 acknowledged")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Acknowledged by: John, Mary")));
        member.setName("Mary");
        assertThat(service.history(event.getId(), session(leader)).getFirst().acknowledgedFirstNames())
            .containsExactly("Mary", "Mary");
        member.setName("<b>John</b> Smith");
        mvc.perform(get(path).session(session(leader)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Acknowledged by: &lt;b&gt;John&lt;/b&gt;, Mary")));
        volunteers.delete(member); volunteers.flush();
        progress = service.history(event.getId(), session(leader)).getFirst();
        assertThat(progress.acknowledged()).isEqualTo(2);
        assertThat(progress.acknowledgedFirstNames()).containsExactly("Mary", "Unknown user");
        mvc.perform(get(path).session(session(leader)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Acknowledged by: Mary, Unknown user")));
    }
    @Test void readOnlyAnnouncementsDoNotDisplayAcknowledgmentNames() throws Exception {
        long id = send(false);
        service.mark(id, session(member), false);
        mvc.perform(get("/events/" + event.getId() + "/announcements").session(session(leader)))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Acknowledged by:"))));
    }
    @Test void permissionsAndRecipientEndpointsRejectForgedAccess() throws Exception {
        String path = "/events/" + event.getId() + "/announcements";
        mvc.perform(get(path + "/audience").param("departmentId", parking.getId().toString()).session(session(leader)))
            .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get(path + "/audience").param("departmentId", other.getId().toString()).session(session(leader)))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).session(session(member)).with(csrf()).param("departmentId", parking.getId().toString()).param("title", "X").param("message", "Y"))
            .andExpect(status().isForbidden());
        long id = send(true);
        mvc.perform(post("/api/notifications/" + id + "/acknowledge").session(session(outsider)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/read").session(session(outsider)).with(csrf()).contentType("application/json").content("[" + id + "]"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/" + id + "/acknowledge").session(session(member))).andExpect(status().isForbidden());
        mvc.perform(post(path + "/broadcast").session(session(leader)).with(csrf()).param("message", "Global"))
            .andExpect(status().isForbidden());
    }
    @Test void senderRolesAndEventBoundaries() {
        assertThat(access.canAnnounce(session(leader), event.getId(), parking.getId())).isTrue();
        leader.getDepartmentRoles().put(parking, Volunteer.Role.ASSISTANT_OVERSEER); volunteers.flush();
        assertThat(access.canAnnounce(session(leader), event.getId(), parking.getId())).isTrue();
        leader.getDepartmentRoles().put(parking, Volunteer.Role.CAPTAIN); volunteers.flush();
        assertThat(access.canAnnounce(session(leader), event.getId(), parking.getId())).isFalse();
        leader.setEventAdministrator(true); volunteers.flush();
        assertThat(access.canAnnounce(session(leader), event.getId(), other.getId())).isTrue();
        var another = scheduler.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2028-06-04"));
        assertThatThrownBy(() -> service.preview(another.getId(), parking.getId(), session(leader)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void rendersComposerAndValidatesDraftsAndEmptyAudience() throws Exception {
        String path = "/events/" + event.getId() + "/announcements";
        mvc.perform(get(path).session(session(leader))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Acknowledgment required")));
        mvc.perform(post(path).session(session(leader)).with(csrf()).param("departmentId", parking.getId().toString()).param("title", " ").param("message", "draft"))
            .andExpect(flash().attribute("draftMessage", "draft"));
        assertThat(announcements.findByEventIdOrderByCreatedAtDescIdDesc(event.getId())).isEmpty();
        leader.setEventAdministrator(true); volunteers.flush();
        var empty = scheduler.saveDepartment(event.getId(), null, Map.of("name", "Empty"));
        assertThatThrownBy(() -> service.send(event.getId(), empty.getId(), "Title", "Body", false, session(leader)))
            .isInstanceOf(IllegalArgumentException.class);
        long id = send(false); service.mark(id, session(member), false);
        assertThat(service.pending(event.getId(), session(member))).isEmpty();
        scheduler.deleteEvent(event.getId());
        assertThat(recipients.findByAnnouncementId(id)).isEmpty();
        assertThat(announcements.existsById(id)).isFalse();
    }
}
