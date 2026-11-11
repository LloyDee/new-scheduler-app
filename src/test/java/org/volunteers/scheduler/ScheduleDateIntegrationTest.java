package org.volunteers.scheduler;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.volunteers.scheduler.service.PhoneAccess;
import org.volunteers.scheduler.service.SchedulerService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercise date changes without an enclosing test transaction. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:schedule-date-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class ScheduleDateIntegrationTest {
    @Autowired SchedulerService service;
    @Autowired MockMvc mvc;

    @Test void conventionDateFiltersRenderAcrossSeparateRequests() throws Exception {
        var event = service.saveEvent(null, Map.of("type", "REGIONAL_CONVENTION", "startDate", "2027-06-04"));
        var department = service.saveDepartment(event.getId(), null, Map.of("name", "Date filter team"));
        var volunteer = service.saveVolunteer(event.getId(), null, Map.of("name", "Date filter volunteer",
            "phone", "2105550188", "congregation", "Central", "privilege", "PUBLISHER",
            "departmentRole_" + department.getId(), "OVERSEER"));
        var session = new MockHttpSession();
        session.setAttribute(PhoneAccess.SESSION, "2105550188");
        String base = "/events/" + event.getId() + "/schedule";
        for (String date : List.of("2027-06-04", "2027-06-05")) {
            var shift = service.saveShift(event.getId(), null, Map.of("name", "Welcome " + date,
                "departmentId", department.getId().toString(), "date", date,
                "startTime", "09:00", "endTime", "10:00", "requiredCount", "1"));
            service.assign(event.getId(), shift.getId(), volunteer.getId(), false);
        }
        for (String date : List.of("2027-06-04", "2027-06-05", "2027-06-06", "")) {
            for (boolean partial : List.of(false, true)) {
                var request = get(base).session(session).param("date", date).param("departmentId", "").param("view", "all");
                if (partial) request.header("X-Scheduler-Section", "main");
                var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
                var page = (org.springframework.data.domain.Page<?>) result.getModelAndView().getModel().get("listPage");
                assertThat(page.getTotalElements()).isEqualTo(date.isEmpty() ? 2 : date.equals("2027-06-06") ? 0 : 1);
            }
        }
    }

}
