package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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

    private static final String EXPERIMENTS = "/api/v1/experiments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private TagRepository tagRepository;

    private Project project;

    @BeforeEach
    void createProject() {
        project = projectRepository.save(new Project("fraud", null));
    }

    @Test
    void createReturns201WithLocationOfExperiment() throws Exception {
        mockMvc.perform(post(experimentsOf(project.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(experimentJson("run-1", "first")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/experiments/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.projectId").value(project.getId().toString()))
                .andExpect(jsonPath("$.name").value("run-1"))
                .andExpect(jsonPath("$.description").value("first"))
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void createInUnknownProjectReturns404() throws Exception {
        mockMvc.perform(post(experimentsOf(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content(experimentJson("run-1", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post(experimentsOf(project.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(experimentJson(" ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void duplicateNameIsRejectedInSameProjectButAllowedInAnother() throws Exception {
        Project other = projectRepository.save(new Project("churn", null));
        createExperiment(project.getId(), "run-1");

        mockMvc.perform(post(experimentsOf(project.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(experimentJson("run-1", null)))
                .andExpect(status().isConflict());
        createExperiment(other.getId(), "run-1");
    }

    @Test
    void getReturnsExperimentWithTagsAndUnknownReturns404() throws Exception {
        String location = createExperiment(project.getId(), "run-1");
        Tag tag = tagRepository.save(new Tag("baseline", null));
        mockMvc.perform(put(location + "/tags/" + tag.getId())).andExpect(status().isNoContent());

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("run-1"))
                .andExpect(jsonPath("$.tags[0].name").value("baseline"));
        mockMvc.perform(get(EXPERIMENTS + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void listReturnsOnlyExperimentsOfProjectWithTotalCountHeader() throws Exception {
        Project other = projectRepository.save(new Project("churn", null));
        createExperiment(project.getId(), "run-1");
        createExperiment(project.getId(), "run-2");
        createExperiment(other.getId(), "run-3");

        mockMvc.perform(get(experimentsOf(project.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("run-1", "run-2")));
    }

    @Test
    void listFiltersByTag() throws Exception {
        Tag baseline = tagRepository.save(new Tag("baseline", null));
        Tag xgboost = tagRepository.save(new Tag("xgboost", null));
        String run1 = createExperiment(project.getId(), "run-1");
        String run2 = createExperiment(project.getId(), "run-2");
        createExperiment(project.getId(), "run-3");
        mockMvc.perform(put(run1 + "/tags/" + baseline.getId())).andExpect(status().isNoContent());
        mockMvc.perform(put(run1 + "/tags/" + xgboost.getId())).andExpect(status().isNoContent());
        mockMvc.perform(put(run2 + "/tags/" + baseline.getId())).andExpect(status().isNoContent());

        mockMvc.perform(get(experimentsOf(project.getId())).param("tagId", baseline.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("run-1", "run-2")));
        mockMvc.perform(get(experimentsOf(project.getId())).param("tagId", xgboost.getId().toString()))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].name").value("run-1"))
                .andExpect(jsonPath("$[0].tags[*].name", containsInAnyOrder("baseline", "xgboost")));
    }

    @Test
    void listCapsPageSizeAtFifty() throws Exception {
        for (int i = 0; i < 51; i++) {
            experimentRepository.save(new Experiment(project, "run-" + i, null));
        }

        mockMvc.perform(get(experimentsOf(project.getId())).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void listOfUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get(experimentsOf(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void updateRenamesAndRejectsNameTakenInProject() throws Exception {
        createExperiment(project.getId(), "run-1");
        String location = createExperiment(project.getId(), "run-2");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(experimentJson("run-3", "new")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("run-3"))
                .andExpect(jsonPath("$.description").value("new"));
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(experimentJson("run-1", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void assignTagIsIdempotentAndRemoveTagDetachesIt() throws Exception {
        String location = createExperiment(project.getId(), "run-1");
        Tag tag = tagRepository.save(new Tag("baseline", null));

        mockMvc.perform(put(location + "/tags/" + tag.getId())).andExpect(status().isNoContent());
        mockMvc.perform(put(location + "/tags/" + tag.getId())).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM experiment_tags", Integer.class)).isEqualTo(1);

        mockMvc.perform(delete(location + "/tags/" + tag.getId())).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(jsonPath("$.tags", hasSize(0)));
        assertThat(tagRepository.existsById(tag.getId())).isTrue();
    }

    @Test
    void assignUnknownTagOrToUnknownExperimentReturns404() throws Exception {
        String location = createExperiment(project.getId(), "run-1");
        Tag tag = tagRepository.save(new Tag("baseline", null));

        mockMvc.perform(put(location + "/tags/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(put(EXPERIMENTS + "/" + UUID.randomUUID() + "/tags/" + tag.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingTagUnassignsItFromExperiments() throws Exception {
        String location = createExperiment(project.getId(), "run-1");
        Tag tag = tagRepository.save(new Tag("baseline", null));
        mockMvc.perform(put(location + "/tags/" + tag.getId())).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/tags/" + tag.getId())).andExpect(status().isNoContent());

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void deleteReturns204ThenUnknownReturns404() throws Exception {
        String location = createExperiment(project.getId(), "run-1");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(delete(location)).andExpect(status().isNotFound());
    }

    @Test
    void deletingProjectRemovesItsExperiments() throws Exception {
        createExperiment(project.getId(), "run-1");

        mockMvc.perform(delete("/api/v1/projects/" + project.getId())).andExpect(status().isNoContent());

        assertThat(experimentRepository.count()).isZero();
    }

    private String createExperiment(UUID projectId, String name) throws Exception {
        String location = mockMvc.perform(post(experimentsOf(projectId)).contentType(MediaType.APPLICATION_JSON)
                        .content(experimentJson(name, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return location;
    }

    private static String experimentsOf(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/experiments";
    }

    private static String experimentJson(String name, String description) {
        return description == null
                ? """
                {"name": "%s"}
                """.formatted(name)
                : """
                {"name": "%s", "description": "%s"}
                """.formatted(name, description);
    }
}
