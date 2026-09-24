package ru.itmo.highload_ml.tracking.api;

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
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.repository.MetricRepository;
import ru.itmo.highload_ml.tracking.service.RunService;

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
class MetricControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RunService runService;
    @Autowired private MetricRepository metricRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;

    @Test
    void createsReadsAndListsMetricsWithTotalCount() throws Exception {
        UUID runId = createRunningRun();
        String collection = metrics(runId);
        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"accuracy\",\"value\":0.95,\"step\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/runs/[0-9a-f-]{36}/metrics/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.name").value("accuracy"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()));

        mockMvc.perform(post(collection + "/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metrics":[
                                  {"name":"loss","value":0.2,"step":1},
                                  {"name":"accuracy","value":0.97,"step":2}
                                ]}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get(collection).param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(metrics(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(get(collection + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidDuplicatesAndCompletedRun() throws Exception {
        UUID runId = createRunningRun();
        String collection = metrics(runId);
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.value").exists())
                .andExpect(jsonPath("$.validationErrors.step").exists());
        mockMvc.perform(post(collection + "/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.metrics").exists());
        mockMvc.perform(post(collection + "/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"name\":\"x\",\"value\":1,\"step\":-1}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"accuracy\",\"value\":0.95,\"step\":1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"accuracy\",\"value\":0.96,\"step\":1}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(collection + "/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metrics":[
                                  {"name":"loss","value":0.2,"step":1},
                                  {"name":"loss","value":0.3,"step":1}
                                ]}
                                """))
                .andExpect(status().isConflict());
        assertThat(metricRepository.count()).isEqualTo(1);

        runService.complete(runId);
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"loss\",\"value\":0.2,\"step\":1}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get(collection).param("size", "0")).andExpect(status().isBadRequest());
    }

    private UUID createRunningRun() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        Experiment experiment = experimentRepository.saveAndFlush(new Experiment(project, "baseline", null));
        User user = userRepository.saveAndFlush(new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, user, ProjectRole.EDITOR));
        UUID runId = runService.create(experiment.getId(), new CreateRunRequest(user.getId(), "training")).id();
        runService.start(runId);
        return runId;
    }

    private static String metrics(UUID runId) {
        return "/api/v1/runs/" + runId + "/metrics";
    }
}
