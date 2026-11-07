package org.volunteers.scheduler;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.volunteers.scheduler.repository.DeviceVisits;
import org.volunteers.scheduler.service.DeviceVisitService;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:logs-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class LogsIntegrationTest extends ManagerAccessFixture {
    @Autowired MockMvc mvc;
    @Autowired DeviceVisits visits;
    @Autowired DeviceVisitService service;
    @BeforeEach void clear() { visits.deleteAll(); }

    @Test void authorizedRolesOpenLogsWithoutPasswordAndOthersReceive403() throws Exception {
        service.record(UUID.randomUUID().toString(), Map.of("city", "PrivateCity"));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/logs").session(session)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("PrivateCity")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Unlock Logs"))));
        org.mockito.Mockito.when(phoneAccess.canManageLogsAndNotifications(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(false);
        mvc.perform(get("/logs").session(session)).andExpect(status().isForbidden());
    }
    @Test void recordingUsesPersistentCookieAndPreservesLocation() throws Exception {
        mvc.perform(post("/api/device-visits").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        var result = mvc.perform(post("/api/device-visits").with(csrf()).contentType("application/json")
            .content("{\"browser\":\"Firefox 140\",\"city\":\"Austin\"}"))
            .andExpect(status().isNoContent()).andReturn();
        Cookie cookie = result.getResponse().getCookie("schedulerDevice");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        mvc.perform(post("/api/device-visits").with(csrf()).cookie(cookie).contentType("application/json").content("{}"))
            .andExpect(status().isNoContent());
        var record = visits.findById(cookie.getValue()).orElseThrow();
        assertThat(record.visitCount).isEqualTo(1);
        assertThat(record.city).isEqualTo("Austin");
        assertThat(record.name).isNull();
        assertThat(record.lastSeen).isAfterOrEqualTo(record.firstSeen);
        mvc.perform(post("/api/device-visits").with(csrf()).contentType("application/json").content("{}"));
        assertThat(visits.count()).isEqualTo(2);
    }

    @Test void concurrentUpdatesCountOneActivityWindow() throws Exception {
        String id = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++) futures.add(executor.submit(() -> service.record(id, Map.of())));
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(visits.findById(id).orElseThrow().visitCount).isEqualTo(1);
    }

    @Test void activityWithinThreeMinutesPreservesCountAndLastSeen() {
        String id = UUID.randomUUID().toString();
        service.record(id, Map.of(), "Alex");
        var visit = visits.findById(id).orElseThrow();
        visit.lastSeen = java.time.Instant.now().minusSeconds(120);
        visits.save(visit);
        var lastSeen = visits.findById(id).orElseThrow().lastSeen;
        service.record(id, Map.of(), "Alex");
        var updated = visits.findById(id).orElseThrow();
        assertThat(updated.visitCount).isEqualTo(1);
        assertThat(updated.lastSeen).isEqualTo(lastSeen);
    }

    @Test void expiredWindowRecordsNewActivityAndDifferentPeopleRemainSeparate() {
        String id = UUID.randomUUID().toString();
        service.record(id, Map.of("city", "Austin"), "Alex");
        var visit = visits.findById(id).orElseThrow();
        visit.lastSeen = java.time.Instant.now().minusSeconds(181);
        visits.save(visit);
        service.record(id, Map.of(), "Alex");
        var updated = visits.findById(id).orElseThrow();
        assertThat(updated.visitCount).isEqualTo(2);
        assertThat(updated.city).isEqualTo("Austin");
        service.record("another-person", Map.of(), "Sam");
        assertThat(visits.findById("another-person").orElseThrow().visitCount).isEqualTo(1);
    }

    @Test void emptyPaginationAndEscaping() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/logs").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("No device visits recorded yet.")));
        for (int i = 0; i < 26; i++) service.record(UUID.randomUUID().toString(), Map.of("browser", "<script>alert(1)</script>"));
        mvc.perform(get("/logs").session(session)).andExpect(status().isOk())
            .andExpect(model().attribute("logs", org.hamcrest.Matchers.hasProperty("numberOfElements", org.hamcrest.Matchers.is(10))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 1 of 3")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Next")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        mvc.perform(get("/logs").param("page", "1").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Previous")));
        mvc.perform(get("/logs").param("page", "2").session(session)).andExpect(status().isOk())
            .andExpect(model().attribute("logs", org.hamcrest.Matchers.hasProperty("numberOfElements", org.hamcrest.Matchers.is(6))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 3 of 3")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Next</a>"))));
    }
    @Test void newestActivityComesFirstAndPageHasNoBackLinkOrFilters() throws Exception {
        for (int i = 0; i < 12; i++) {
            var visit = new org.volunteers.scheduler.model.DeviceVisit();
            visit.id = "device-" + i; visit.name = "Alex";
            visit.firstSeen = java.time.Instant.parse("2030-01-01T00:00:00Z");
            visit.lastSeen = visit.firstSeen.plusSeconds(i); visits.save(visit);
        }
        var result = mvc.perform(get("/logs"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Back to Settings"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Times shown in UTC."))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Visits count activity windows"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("12:00 AM UTC")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-log-time datetime=\"2030-01-01T00:00:11Z\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-logs-timezone>UTC</span>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/logs.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/logs?page=1\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("logs-filters"))))
            .andReturn();
        var logs = (org.springframework.data.domain.Page<?>) result.getModelAndView().getModel().get("logs");
        assertThat(logs.getTotalElements()).isEqualTo(12);
        assertThat(logs.getNumberOfElements()).isEqualTo(10);
        assertThat(logs.getContent().stream().map(v -> ((org.volunteers.scheduler.model.DeviceVisit) v).id).toList())
            .containsExactly("device-11", "device-10", "device-9", "device-8", "device-7", "device-6", "device-5", "device-4", "device-3", "device-2");
    }
}
