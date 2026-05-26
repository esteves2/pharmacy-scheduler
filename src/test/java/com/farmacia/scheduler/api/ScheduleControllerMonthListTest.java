package com.farmacia.scheduler.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// March 2026: March 1 is a Sunday.
// previousOrSame(MONDAY) on March 1 = Feb 23 = ISO week 9.
// Weeks included: 9 (Feb 23), 10 (Mar 2), 11 (Mar 9), 12 (Mar 16), 13 (Mar 23), 14 (Mar 30) = 6 weeks.
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerMonthListTest {

    @DynamicPropertySource
    static void isolatedSqlite(DynamicPropertyRegistry registry) throws IOException {
        Path tempDir = Files.createTempDirectory("pharmacy-test-monthlist-");
        String url = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    }

    @Autowired MockMvc mockMvc;

    @Test
    void listByMonth_march2026_returnsSixWeeks() throws Exception {
        mockMvc.perform(get("/api/schedules/weeks").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].isoYear").value(2026))
                .andExpect(jsonPath("$[0].isoWeek").value(9))
                .andExpect(jsonPath("$[0].weekStart").value("2026-02-23"))
                .andExpect(jsonPath("$[0].weekEnd").value("2026-03-01"))
                .andExpect(jsonPath("$[5].isoWeek").value(14))
                .andExpect(jsonPath("$[5].weekStart").value("2026-03-30"));
    }

    @Test
    void listByMonth_statusNullForUngeneratedWeeks() throws Exception {
        mockMvc.perform(get("/api/schedules/weeks").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                // All weeks ungenerated — status serialises as JSON null
                .andExpect(jsonPath("$[0].status", nullValue()))
                .andExpect(jsonPath("$[1].status", nullValue()));
    }

    @Test
    void listByMonth_statusReflectsGeneratedWeek() throws Exception {
        mockMvc.perform(post("/api/schedules/weeks/2026/10/generate"))
                .andExpect(status().isCreated());

        // Week 10 is index 1 in the March list (after week 9 at index 0)
        mockMvc.perform(get("/api/schedules/weeks").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].isoWeek").value(10))
                .andExpect(jsonPath("$[1].status").value("DRAFT"));
    }

    @Test
    void listByMonth_january2026_returnsFiveWeeks() throws Exception {
        // January 1, 2026 is a Thursday.
        // previousOrSame(MONDAY) = Dec 29, 2025 = ISO week 1 of 2026.
        // Weeks: 1 (Dec 29), 2 (Jan 5), 3 (Jan 12), 4 (Jan 19), 5 (Jan 26) = 5 weeks.
        mockMvc.perform(get("/api/schedules/weeks").param("year", "2026").param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].isoWeek").value(1))
                .andExpect(jsonPath("$[0].weekStart").value("2025-12-29"));
    }
}
