package org.volunteers.scheduler;

import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.volunteers.scheduler.model.*;
import org.volunteers.scheduler.repository.*;
import org.volunteers.scheduler.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:transfer-tests;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "scheduler.app-owner-phone=9995550000"})
@AutoConfigureMockMvc
class VolunteerTransferIntegrationTest {
    @Autowired SchedulerService scheduler;
    @Autowired VolunteerTransferService transfers;
    @Autowired Volunteers volunteers;
    @Autowired VolunteerTransferAudits audits;
    @Autowired Assignments assignments;
    @Autowired Shifts shifts;
    @Autowired PhoneAccess access;
    @Autowired BackupService backups;
    @Autowired MockMvc mvc;
    long source, destination, department;
    Volunteer person;
    MockHttpSession owner;
    @BeforeEach void setup() {
        owner = new MockHttpSession(); owner.setAttribute(PhoneAccess.SESSION, "9995550000");
        source = event("Source"); destination = event("Destination");
        department = scheduler.saveDepartment(destination, null, Map.of("name", "Parking")).getId();
        person = scheduler.saveVolunteer(source, null, Map.of("name", "Alex Rivera", "phone", "8135550123",
            "email", "alex@example.com", "congregation", "North", "privilege", "ELDER", "departmentChoice", "none"));
    }
    long event(String name) {
        return scheduler.saveEvent(null, Map.of("name", name + UUID.randomUUID(), "type", "REGIONAL_CONVENTION",
            "startDate", "2027-01-01", "endDate", "2027-01-02", "venue", "Hall")).getId();
    }
    void execute(VolunteerTransferService.Action action) {
        transfers.execute(source, destination, List.of(person.getId()), action, owner);
    }
    @Test void addRetainsProfileAndSourceAndRejectsDuplicateWithoutOverwriting() {
        long before = audits.count();
        execute(VolunteerTransferService.Action.ADD);
        Volunteer copy = volunteers.findByEventIdOrderByNameAscIdAsc(destination).stream().filter(v -> v.getName().equals(person.getName())).findFirst().orElseThrow();
        assertThat(copy.getPhone()).isEqualTo(person.getPhone()); assertThat(copy.getPrivilege()).isEqualTo(person.getPrivilege());
        assertThat(copy.getEmail()).isEqualTo(person.getEmail()); assertThat(copy.getCongregation()).isEqualTo(person.getCongregation());
        assertThat(copy.getDepartmentRoles()).isEmpty();
        assertThat(copy.isEventAdministrator()).isFalse();
        assertThat(volunteers.findById(person.getId()).orElseThrow().isParticipationEnded()).isFalse();
        assertThatThrownBy(() -> execute(VolunteerTransferService.Action.MOVE)).hasMessageContaining("Resolve");
        assertThat(audits.count()).isEqualTo(before + 1);
        assertThat(volunteers.findById(person.getId()).orElseThrow().isParticipationEnded()).isFalse();
        assertThat(transfers.review(source, destination, List.of(person.getId()),
            VolunteerTransferService.Action.ADD, owner).rows().getFirst().status()).isEqualTo("Phone number already exists in the destination event");
        assertThat(assignments.findByShiftDepartmentEventIdOrderByIdAsc(destination)).isEmpty();
    }
    @Test void duplicatePhoneBlocksAddAndMoveAndIsRecheckedAfterReview() {
        Volunteer existing = new Volunteer(); existing.setEvent(scheduler.event(destination));
        existing.setName("Different Person"); existing.setPhone("+1 (813) 555-0123");
        for (var action : VolunteerTransferService.Action.values()) {
            assertThat(transfers.review(source, destination, List.of(person.getId()), action, owner)
                .rows().getFirst().status()).isEqualTo("Ready");
            existing = volunteers.save(existing);
            long before = audits.count();
            assertThat(transfers.review(source, destination, List.of(person.getId()), action, owner)
                .rows().getFirst().status()).isEqualTo("Phone number already exists in the destination event");
            assertThatThrownBy(() -> execute(action)).hasMessageContaining("Resolve");
            assertThat(volunteers.findByEventIdOrderByNameAscIdAsc(destination)).extracting(Volunteer::getId)
                .containsExactly(existing.getId());
            assertThat(volunteers.findById(person.getId()).orElseThrow().isParticipationEnded()).isFalse();
            assertThat(audits.count()).isEqualTo(before);
            volunteers.delete(existing);
            existing = new Volunteer(); existing.setEvent(scheduler.event(destination));
            existing.setName("Different Person"); existing.setPhone("+1 (813) 555-0123");
        }
    }
    @Test void sharedPhoneWithinSelectionBlocksEntireTransfer() {
        Volunteer other = new Volunteer(); other.setEvent(scheduler.event(source));
        other.setName("Shared Phone"); other.setPhone("(813) 555-0123"); volunteers.save(other);
        for (var action : VolunteerTransferService.Action.values()) {
            var ids = List.of(person.getId(), other.getId());
            assertThat(transfers.review(source, destination, ids, action, owner).rows())
                .extracting(VolunteerTransferService.Row::status).contains("Phone number is shared by selected volunteers");
            assertThatThrownBy(() -> transfers.execute(source, destination, ids, action, owner)).hasMessageContaining("Resolve");
            assertThat(volunteers.findByEventIdOrderByNameAscIdAsc(destination)).isEmpty();
            assertThat(volunteers.findById(person.getId()).orElseThrow().isParticipationEnded()).isFalse();
        }
    }
    @Test void duplicatePhoneDisablesConfirmation() throws Exception {
        Volunteer existing = new Volunteer(); existing.setEvent(scheduler.event(destination));
        existing.setName("Existing Profile"); existing.setPhone(person.getPhone()); volunteers.save(existing);
        for (var action : VolunteerTransferService.Action.values()) {
            mvc.perform(get("/events/" + source + "/volunteers/transfer").session(owner)
                .param("ids", person.getId().toString()).param("destination", "" + destination)
                .param("action", action.name()).param("review", "true"))
                .andExpect(status().isOk()).andExpect(model().attribute("blocked", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phone number already exists in the destination event")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<button disabled=\"disabled\">Confirm Add/Move</button>")));
        }
    }
    @Test void moveEndsParticipationAndSurvivesBackupRestore() {
        execute(VolunteerTransferService.Action.MOVE);
        assertThat(volunteers.findById(person.getId()).orElseThrow().isParticipationEnded()).isTrue();
        assertThat(scheduler.volunteerPage(source, null, "Alex", 0, List.of()).page()).isEmpty();
        assertThat(scheduler.snapshot(source).volunteers()).noneMatch(v -> v.getId().equals(person.getId()));
        var backup = backups.capture(source); backups.restore(source, backup.files());
        assertThat(volunteers.findByEventIdOrderByNameAscIdAsc(source).stream().filter(v -> v.getName().equals("Alex Rivera")).findFirst().orElseThrow().isParticipationEnded()).isTrue();
    }
    @Test void upcomingAssignmentBlocksEntireMove() {
        Department d = scheduler.saveDepartment(source, null, Map.of("name", "Source parking"));
        Shift shift = new Shift(); shift.setDepartment(d); shift.setDate(LocalDate.now().plusDays(2));
        shift.setStartTime(LocalTime.NOON); shift.setEndTime(LocalTime.of(13, 0)); shift.setRequiredCount(1); shifts.save(shift);
        scheduler.assign(source, shift.getId(), person.getId(), false);
        assertThatThrownBy(() -> execute(VolunteerTransferService.Action.MOVE)).hasMessageContaining("Resolve");
        assertThat(volunteers.findById(person.getId()).orElseThrow().isParticipationEnded()).isFalse();
        assertThat(volunteers.findByEventIdOrderByNameAscIdAsc(destination)).noneMatch(v -> v.getName().equals(person.getName()));
    }
    @Test void adminMustManageBothEventsAndEndedMembershipLosesAccess() {
        person.setEventAdministrator(true); volunteers.save(person);
        MockHttpSession admin = new MockHttpSession(); admin.setAttribute(PhoneAccess.SESSION, person.getPhone());
        assertThatThrownBy(() -> transfers.review(source, destination, List.of(person.getId()), VolunteerTransferService.Action.ADD, admin)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(transfers.destinations(source, admin)).noneMatch(e -> e.getId() == destination);
        Volunteer destinationAdmin = new Volunteer(); destinationAdmin.setEvent(scheduler.event(destination));
        destinationAdmin.setName(person.getName()); destinationAdmin.setPhone(person.getPhone()); destinationAdmin.setEventAdministrator(true); volunteers.save(destinationAdmin);
        assertThat(transfers.destinations(source, admin)).anyMatch(e -> e.getId() == destination);
        // A separate destination permits an actual move rather than the duplicate skip.
        destination = event("Third"); department = scheduler.saveDepartment(destination, null, Map.of("name", "Parking")).getId();
        Volunteer remaining = new Volunteer(); remaining.setEvent(scheduler.event(source)); remaining.setName("Remaining Admin");
        remaining.setPhone("3125550199"); remaining.setEventAdministrator(true); volunteers.save(remaining);
        execute(VolunteerTransferService.Action.MOVE);
        assertThat(access.canAdminister(admin, source)).isFalse();
    }
    @Test void pagesRenderAndConfirmRequiresCsrf() throws Exception {
        mvc.perform(get("/events/" + source + "/volunteers").session(owner)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Add / Move Profile")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("type=\"checkbox\""))));
        mvc.perform(get("/events/" + source + "/volunteers/transfer").session(owner))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Select volunteers")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Search volunteers")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Filter by department")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Destination department"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Destination role"))));
        mvc.perform(get("/events/" + source + "/volunteers/transfer").session(owner).param("ids", person.getId().toString())
            .param("destination", "" + destination).param("department", "" + department).param("review", "true"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm Add/Move")));
        mvc.perform(post("/events/" + source + "/volunteers/transfer").session(owner)
            .param("ids", person.getId().toString()).param("destination", "" + destination).param("department", "" + department))
            .andExpect(status().isForbidden());
        mvc.perform(post("/events/" + source + "/volunteers/transfer").session(owner).with(csrf())
            .param("ids", person.getId().toString()).param("destination", "" + destination).param("department", "" + department))
            .andExpect(status().is3xxRedirection());
        assertThat(volunteers.findByEventIdOrderByNameAscIdAsc(destination).stream()
            .filter(v -> v.getName().equals(person.getName())).findFirst().orElseThrow().getDepartmentRoles()).isEmpty();
    }
    @Test void reviewKeepsFormAndExplainsMissingSelections() throws Exception {
        String path = "/events/" + source + "/volunteers/transfer";
        mvc.perform(get(path).session(owner).param("review", "true"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("reviewWarning", "Select at least one volunteer and a destination event before reviewing."))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"alert\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No Department Yet / Volunteer"))));
        mvc.perform(get(path).session(owner).param("review", "true").param("destination", "" + destination))
            .andExpect(status().isOk())
            .andExpect(model().attribute("destination", destination))
            .andExpect(model().attribute("reviewWarning", "Select at least one volunteer before reviewing."));
        mvc.perform(get(path).session(owner).param("review", "true").param("ids", person.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("ids", List.of(person.getId())))
            .andExpect(model().attribute("reviewWarning", "Select a destination event before reviewing."));
    }
    @Test void selectionSurvivesSearchAndDepartmentFiltering() throws Exception {
        Department d = scheduler.saveDepartment(source, null, Map.of("name", "Attendants"));
        Volunteer other = scheduler.saveVolunteer(source, null, Map.of("name", "Morgan James Rivera", "phone", "3125550188",
            "congregation", "South", "privilege", "PUBLISHER", "departmentRole_" + d.getId(), "VOLUNTEER"));
        mvc.perform(get("/events/" + source + "/volunteers/transfer").session(owner)
            .param("ids", person.getId().toString()).param("q", "Morgan").param("departmentId", d.getId().toString()))
            .andExpect(status().isOk()).andExpect(model().attribute("retainedIds", List.of(person.getId())))
            .andExpect(model().attribute("selectionNames", Map.of(other.getId(), "Morgan Rivera")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"tooltip\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-transfer-results")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Search / Filter"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"" + other.getId() + "\"")));
        mvc.perform(get("/events/" + source + "/volunteers/transfer").session(owner)
            .param("q", "Alex").param("departmentId", d.getId().toString()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("No volunteers match")));
    }
}
