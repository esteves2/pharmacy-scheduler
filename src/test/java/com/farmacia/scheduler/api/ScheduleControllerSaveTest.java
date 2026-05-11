package com.farmacia.scheduler.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the HTTP layer end-to-end: specifically that an ISO-8601 date string in the
// AssignmentWriteRequest JSON body is deserialised to LocalDate on the server side.
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerSaveTest {

    @DynamicPropertySource
    static void isolatedSqlite(DynamicPropertyRegistry registry) throws IOException {
        Path tempDir = Files.createTempDirectory("pharmacy-test-");
        String url = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    }

    @Autowired MockMvc mockMvc;

    @Test
    void save_localDateDeserialises_returns200() throws Exception {
        // Generate week 20 of 2026 (Mon 2026-05-11 — no holidays, clean inputs).
        // This creates the ScheduleWeek row that PUT requires.
        mockMvc.perform(post("/api/schedules/weeks/2026/20/generate"))
                .andExpect(status().isCreated());

        // PUT with a hardcoded JSON body containing an ISO-8601 date string.
        // employeeId 1 = Paula (F), seeded by V2. Date "2026-05-11" is the Monday of week 20.
        // The sole purpose is to verify Jackson deserialises the date field without error.
        String body = """
                {
                  "assignments": [
                    {
                      "employeeId": 1,
                      "date": "2026-05-11",
                      "startTime": "08:00",
                      "endTime": "22:00",
                      "breakStart": null,
                      "breakEnd": null
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/schedules/weeks/2026/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isNotEmpty());
    }
}
