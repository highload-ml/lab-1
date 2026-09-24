package ru.itmo.highload_ml.tracking.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RunControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;
    @Autowired private RunRepository runRepository;

    @Test
    void createsReadsAndTransitionsRunWithCorrectStatuses() throws Exception {
        Context context = createContext();
        String collection = "/api/v1/experiments/" + context.experimentId() + "/runs";
        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorId\":\"" + context.userId() + "\",\"name\":\"training\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/runs/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();

        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("training"));
        mockMvc.perform(post(location + "/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.startedAt").exists());
        mockMvc.perform(post(location + "/complete")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finishedAt").exists());
        mockMvc.perform(post(location + "/fail")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
        mockMvc.perform(get("/api/v1/runs/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void validatesCreateAndMembership() throws Exception {
        Context context = createContext();
        String collection = "/api/v1/experiments/" + context.experimentId() + "/runs";
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.authorId").exists());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorId\":\"" + UUID.randomUUID() + "\",\"name\":\"training\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/experiments/" + UUID.randomUUID() + "/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorId\":\"" + context.userId() + "\",\"name\":\"training\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void experimentWithRunsCannotBeDeleted() throws Exception {
        Context context = createContext();
        UUID runId = runRepository.saveAndFlush(new Run(context.experimentId(), context.userId(), "training"))
                .getId();

        mockMvc.perform(delete("/api/v1/projects/" + context.projectId()
                        + "/experiments/" + context.experimentId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("has runs")));
        assertThat(runRepository.existsById(runId)).isTrue();
        assertThat(experimentRepository.existsById(context.experimentId())).isTrue();
    }

    @Test
    void cursorScrollKeepsTiedTimestampsAndCapsSizeAtFifty() throws Exception {
        Context context = createContext();
        String collection = "/api/v1/experiments/" + context.experimentId() + "/runs";
        Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            UUID runId = runRepository.saveAndFlush(new Run(context.experimentId(), context.userId(), "run-" + i))
                    .getId();
            jdbcTemplate.update("UPDATE runs SET created_at = ? WHERE id = ?", Timestamp.from(sameTime), runId);
            ids.add(runId);
        }
        Context other = createContext();
        runRepository.saveAndFlush(new Run(other.experimentId(), other.userId(), "other"));

        String firstJson = mockMvc.perform(get(collection).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$.items", hasSize(50)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstJson, "$.nextCursor");
        List<String> firstIds = JsonPath.read(firstJson, "$.items[*].id");
        mockMvc.perform(get("/api/v1/experiments/" + other.experimentId() + "/runs")
                        .param("after", cursor))
                .andExpect(status().isBadRequest());
        String secondJson = mockMvc.perform(get(collection).param("size", "100").param("after", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        List<String> secondIds = JsonPath.read(secondJson, "$.items[*].id");
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        assertThat(firstIds.size() + secondIds.size()).isEqualTo(ids.size());

        mockMvc.perform(get(collection).param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(collection).param("after", "invalid")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/experiments/" + UUID.randomUUID() + "/runs"))
                .andExpect(status().isNotFound());
    }

    private Context createContext() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        Experiment experiment = experimentRepository.saveAndFlush(new Experiment(project, "baseline", null));
        User user = userRepository.saveAndFlush(new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, user, ProjectRole.EDITOR));
        return new Context(project.getId(), experiment.getId(), user.getId());
    }

    private record Context(UUID projectId, UUID experimentId, UUID userId) {
    }
}
