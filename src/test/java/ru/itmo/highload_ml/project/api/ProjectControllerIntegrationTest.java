package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

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
class ProjectControllerIntegrationTest extends ProjectModuleIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Test
    void createReturns201WithLocation() throws Exception {
        mockMvc.perform(post(PROJECTS).contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("fraud", "Fraud models")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/projects/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.name").value("fraud"))
                .andExpect(jsonPath("$.description").value("Fraud models"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createWithDuplicateNameReturns409() throws Exception {
        createProject("fraud");

        mockMvc.perform(post(PROJECTS).contentType(MediaType.APPLICATION_JSON).content(projectJson("fraud", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post(PROJECTS).contentType(MediaType.APPLICATION_JSON).content(projectJson(" ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void createWithTooLongDescriptionReturns400() throws Exception {
        mockMvc.perform(post(PROJECTS).contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("fraud", "x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.description").exists());
    }

    @Test
    void getUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get(PROJECTS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsPageWithTotalCountHeader() throws Exception {
        for (int i = 0; i < 3; i++) {
            createProject("p" + i);
        }

        mockMvc.perform(get(PROJECTS).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("p2"));
    }

    @Test
    void listCapsPageSizeAtFifty() throws Exception {
        for (int i = 0; i < 51; i++) {
            projectRepository.save(new Project("p" + i, null));
        }

        mockMvc.perform(get(PROJECTS).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void updateChangesProject() throws Exception {
        String location = createProject("fraud");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(projectJson("fraud-v2", "new")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("fraud-v2"))
                .andExpect(jsonPath("$.description").value("new"));
    }

    @Test
    void updateToTakenNameReturns409() throws Exception {
        createProject("fraud");
        String other = createProject("churn");

        mockMvc.perform(put(other).contentType(MediaType.APPLICATION_JSON).content(projectJson("fraud", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRemovesProjectAndItsMemberships() throws Exception {
        Project project = projectRepository.save(new Project("fraud", null));
        User alice = userRepository.save(new User("alice", "hash", UserRole.ML_ENGINEER));
        membershipRepository.save(new ProjectMembership(project, alice, ProjectRole.OWNER));

        mockMvc.perform(delete(PROJECTS + "/" + project.getId())).andExpect(status().isNoContent());

        assertThat(projectRepository.existsById(project.getId())).isFalse();
        assertThat(membershipRepository.count()).isZero();
        assertThat(userRepository.existsById(alice.getId())).isTrue();
        mockMvc.perform(delete(PROJECTS + "/" + project.getId())).andExpect(status().isNotFound());
    }

    private String createProject(String name) throws Exception {
        String location = mockMvc.perform(post(PROJECTS).contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(name, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return location;
    }

    private static String projectJson(String name, String description) {
        return description == null
                ? """
                {"name": "%s"}
                """.formatted(name)
                : """
                {"name": "%s", "description": "%s"}
                """.formatted(name, description);
    }
}
