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
import ru.itmo.highload_ml.tracking.model.Artifact;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.repository.ArtifactRepository;
import ru.itmo.highload_ml.tracking.repository.RunRepository;
import ru.itmo.highload_ml.tracking.service.RunService;

import java.util.ArrayList;
import java.util.List;
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
class ArtifactControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RunService runService;
    @Autowired private RunRepository runRepository;
    @Autowired private ArtifactRepository artifactRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;

    @Test
    void createsReadsAndListsMetadataWithPageCap() throws Exception {
        UUID runId = createRunningRun();
        String collection = artifacts(runId);
        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(request("weights")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/runs/[0-9a-f-]{36}/artifacts/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.type").value("MODEL"))
                .andExpect(jsonPath("$.path").value("models/weights.bin"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()));

        var run = runRepository.findById(runId).orElseThrow();
        List<Artifact> more = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            more.add(new Artifact(run, "artifact-" + i, ArtifactType.OTHER, "objects/" + i, i));
        }
        artifactRepository.saveAllAndFlush(more);
        mockMvc.perform(get(collection).param("size", "100"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(50)));
        mockMvc.perform(get(collection).param("page", "1").param("size", "50"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void rejectsInvalidDuplicateAndCompletedRun() throws Exception {
        UUID runId = createRunningRun();
        String collection = artifacts(runId);
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.type").exists())
                .andExpect(jsonPath("$.validationErrors.path").exists())
                .andExpect(jsonPath("$.validationErrors.sizeBytes").exists());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(request("weights")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(request("weights")))
                .andExpect(status().isConflict());

        runService.complete(runId);
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(request("new")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get(artifacts(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(get(collection + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get(collection).param("page", "-1")).andExpect(status().isBadRequest());
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

    private static String artifacts(UUID runId) {
        return "/api/v1/runs/" + runId + "/artifacts";
    }

    private static String request(String name) {
        return "{\"name\":\"" + name + "\",\"type\":\"MODEL\",\"path\":\"models/weights.bin\",\"sizeBytes\":1024}";
    }
}
