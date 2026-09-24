package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TagControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void createsAndListsGlobalTags() throws Exception {
        String location = mockMvc.perform(post("/api/v1/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/tags/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.name").value("important"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();

        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("important"));
        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/tags/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankAndDuplicateTagNames() throws Exception {
        mockMvc.perform(post("/api/v1/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.name").exists());
        mockMvc.perform(post("/api/v1/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/tags").param("size", "0")).andExpect(status().isBadRequest());
    }
}
