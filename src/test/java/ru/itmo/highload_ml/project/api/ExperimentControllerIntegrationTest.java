package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ExperimentControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private TagRepository tagRepository;

    @Test
    void crudUsesProjectScopedUrisAndHttpStatuses() throws Exception {
        UUID projectId = projectRepository.saveAndFlush(new Project("fraud", null)).getId();
        UUID otherProjectId = projectRepository.saveAndFlush(new Project("churn", null)).getId();
        String collection = experiments(projectId);

        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"name":"baseline","description":"first version"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/projects/[0-9a-f-]{36}/experiments/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("baseline"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        UUID experimentId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get(experiments(otherProjectId) + "/" + experimentId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"baseline-v2\",\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("updated"));
        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        assertThat(experimentRepository.existsById(experimentId)).isFalse();
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
        mockMvc.perform(delete(location)).andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByTagAndEnforcesPageLimit() throws Exception {
        UUID projectId = projectRepository.saveAndFlush(new Project("fraud", null)).getId();
        UUID otherProjectId = projectRepository.saveAndFlush(new Project("churn", null)).getId();
        String collection = experiments(projectId);
        String first = createExperiment(collection, "first");
        createExperiment(collection, "second");
        createExperiment(experiments(otherProjectId), "third");
        String tagLocation = mockMvc.perform(post("/api/v1/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        UUID tagId = UUID.fromString(tagLocation.substring(tagLocation.lastIndexOf('/') + 1));

        mockMvc.perform(put(first + "/tags/" + tagId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("important"));
        mockMvc.perform(put(first + "/tags/" + tagId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(1)));
        mockMvc.perform(get(collection).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(collection).param("tagId", tagId.toString()))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].name").value("first"));
        mockMvc.perform(delete(first + "/tags/" + tagId)).andExpect(status().isNoContent());
        mockMvc.perform(delete(first + "/tags/" + tagId)).andExpect(status().isNoContent());
        assertThat(tagRepository.existsById(tagId)).isTrue();

        for (int i = 0; i < 49; i++) {
            createExperiment(collection, "extra-" + i);
        }
        mockMvc.perform(get(collection).param("size", "100"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void invalidRequestsAndMissingReferencesReturnProblemDetails() throws Exception {
        UUID projectId = projectRepository.saveAndFlush(new Project("fraud", null)).getId();
        String collection = experiments(projectId);
        String first = createExperiment(collection, "first");
        createExperiment(collection, "taken");

        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.name").exists());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"first\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"taken\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(first + "/tags/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get(collection).param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(collection).param("tagId", "bad-id")).andExpect(status().isBadRequest());
        mockMvc.perform(get(experiments(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    private String createExperiment(String collection, String name) throws Exception {
        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return location;
    }

    private static String experiments(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/experiments";
    }
}
