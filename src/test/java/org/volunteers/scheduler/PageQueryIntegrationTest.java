package org.volunteers.scheduler;

import java.util.Map;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.volunteers.scheduler.service.SchedulerService;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:page-query-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.generate_statistics=true", "logging.level.org.hibernate.stat=ERROR",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=ERROR"})
@Transactional
class PageQueryIntegrationTest {
    @Autowired SchedulerService service;
    @Autowired EntityManager em;

    @Test void pageSnapshotDoesNotQueryRolesForEachVolunteer() {
        var event = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-04"));
        var department = service.saveDepartment(event.getId(), null, Map.of("name", "Attendants"));
        for (int i = 0; i < 12; i++) {
            service.saveVolunteer(event.getId(), null, Map.of("name", "Volunteer " + i,
                "phone", String.format("210555%04d", i), "congregation", "Central", "privilege", "PUBLISHER",
                "departmentRole_" + department.getId(), "VOLUNTEER"));
        }
        em.flush();
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var snapshot = service.snapshot(event.getId());
        assertThat(snapshot.volunteers()).hasSize(12);
        snapshot.volunteers().stream().filter(v -> !v.isEventAdministrator()).forEach(v -> assertThat(v.getDepartmentRoles()).hasSize(1));
        assertThat(statistics.getPrepareStatementCount()).as("SQL statements to load one page snapshot").isEqualTo(5);
    }
    @Test void databasePagesAndHomeLoadOnlyRelevantRecords() {
        var event = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-04"));
        var departments = new java.util.ArrayList<org.volunteers.scheduler.model.Department>();
        var shifts = new java.util.ArrayList<org.volunteers.scheduler.model.Shift>();
        for (int i = 0; i < 31; i++) {
            var d = service.saveDepartment(event.getId(), null, Map.of("name", String.format("Department %02d", i)));
            departments.add(d);
            shifts.add(service.saveShift(event.getId(), null, Map.of("name", "Shift " + i,
                "departmentId", d.getId().toString(), "date", "2027-06-04", "startTime", "09:00", "endTime", "10:00", "requiredCount", "1")));
        }
        var person = service.saveVolunteer(event.getId(), null, Map.of("name", "Alex", "phone", "2105550100",
            "congregation", "Central", "privilege", "PUBLISHER", "departmentRole_" + departments.getFirst().getId(), "OVERSEER"));
        service.assign(event.getId(), shifts.getFirst().getId(), person.getId(), false);
        em.flush(); em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var schedule = service.schedulePage(event.getId(), null, null, "all", java.util.Set.of(), 1, null);
        assertThat(schedule.page().getTotalElements()).isEqualTo(31);
        assertThat(schedule.page().getContent()).hasSize(6);
        assertThat(schedule.snapshot().shifts()).hasSize(6);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        em.clear(); statistics.clear();
        var depts = service.departmentPage(event.getId(), 1, java.util.List.of(departments.getFirst().getId()));
        assertThat(depts.page().getContent()).hasSize(6);
        assertThat(depts.page().getTotalElements()).isEqualTo(31);
        assertThat(depts.snapshot().departments()).hasSize(7);
        assertThat(depts.snapshot().shifts()).hasSize(7);
        assertThat(depts.snapshot().assignments()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        em.clear(); statistics.clear();
        var home = service.home(event.getId(), java.util.Set.of(person.getId()), java.time.LocalDateTime.of(2027, 6, 4, 9, 30));
        assertThat(home.shiftCount()).isEqualTo(31);
        assertThat(home.departmentCount()).isEqualTo(31);
        assertThat(home.volunteerCount()).isEqualTo(1);
        assertThat(home.missing()).isEqualTo(30);
        assertThat(home.snapshot().shifts()).extracting(org.volunteers.scheduler.model.Shift::getId).containsExactly(shifts.getFirst().getId());
        assertThat(home.contacts()).extracting(org.volunteers.scheduler.model.Volunteer::getId).containsExactly(person.getId());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        assertThat(statistics.getEntityLoadCount()).isLessThan(10);
        var mine = service.schedulePage(event.getId(), null, null, "mine", java.util.Set.of(person.getId()), 0, null);
        assertThat(mine.page().getTotalElements()).isEqualTo(1);
        var needs = service.schedulePage(event.getId(), null, null, "needs", java.util.Set.of(), 0, null);
        assertThat(needs.page().getTotalElements()).isEqualTo(30);
        assertThat(service.schedulePage(event.getId(), departments.getFirst().getId(), null, "all", java.util.Set.of(), 0, null).page().getTotalElements()).isEqualTo(1);
        assertThat(service.home(event.getId(), java.util.Set.of(person.getId()), java.time.LocalDateTime.of(2027, 6, 4, 10, 0)).snapshot().shifts()).isEmpty();
    }    @Test void assignmentOptionsExcludeConflictsOutsideThePageAndKeepCurrentAssignments() {
        var event = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-04"));
        var d = service.saveDepartment(event.getId(), null, Map.of("name", "Attendants"));
        var person = service.saveVolunteer(event.getId(), null, Map.of("name", "Alex", "phone", "2105550101",
            "congregation", "Central", "privilege", "PUBLISHER", "departmentRole_" + d.getId(), "VOLUNTEER"));
        var records = new java.util.ArrayList<org.volunteers.scheduler.model.Shift>();
        for (int i = 0; i < 26; i++) records.add(service.saveShift(event.getId(), null, Map.of("name", "Shift " + i,
            "departmentId", d.getId().toString(), "date", "2027-06-04", "startTime", "09:00", "endTime", "10:00", "requiredCount", "1")));
        service.assign(event.getId(), records.getFirst().getId(), person.getId(), false);
        em.flush(); em.clear();
        var result = service.schedulePage(event.getId(), null, null, "all", java.util.Set.of(), 1, records.getFirst().getId());
        assertThat(result.page().getContent()).hasSize(1);
        assertThat(result.options().get(records.getLast().getId())).isEmpty();
        assertThat(result.options().get(records.getFirst().getId())).extracting(org.volunteers.scheduler.model.Volunteer::getId).containsExactly(person.getId());
        var other = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-11"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.schedulePage(other.getId(), null, null, "all", java.util.Set.of(), 0, records.getFirst().getId()));
    }}
