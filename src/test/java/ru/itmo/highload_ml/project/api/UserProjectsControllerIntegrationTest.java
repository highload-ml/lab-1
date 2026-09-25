package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/users/{id}/projects: the "My projects" list of the web client.
 */
@AutoConfigureMockMvc
class UserProjectsControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void createUsers() {
        alice = userRepository.save(new User("alice", null, UserRole.ML_ENGINEER));
        bob = userRepository.save(new User("bob", null, UserRole.REVIEWER));
    }

    @Test
    void listsOnlyProjectsTheUserBelongsToWithTheirRole() throws Exception {
        Project fraud = projectRepository.save(new Project("fraud", "Fraud models"));
        Project churn = projectRepository.save(new Project("churn", null));
        Project foreign = projectRepository.save(new Project("foreign", null));
        membershipRepository.save(new ProjectMembership(fraud, alice, ProjectRole.OWNER));
        membershipRepository.save(new ProjectMembership(churn, alice, ProjectRole.VIEWER));
        membershipRepository.save(new ProjectMembership(foreign, bob, ProjectRole.OWNER));

        mockMvc.perform(get(projectsOf(alice.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("fraud", "churn")))
                .andExpect(jsonPath("$[?(@.name == 'fraud')].role").value("OWNER"))
                .andExpect(jsonPath("$[?(@.name == 'fraud')].description").value("Fraud models"))
                .andExpect(jsonPath("$[?(@.name == 'churn')].role").value("VIEWER"))
                .andExpect(jsonPath("$[0].projectId").exists())
                .andExpect(jsonPath("$[0].joinedAt").exists());
    }

    @Test
    void projectCreatedByUserAppearsInTheirListAsOwner() throws Exception {
        mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "fraud", "ownerId": "%s"}
                                """.formatted(alice.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(projectsOf(alice.getId())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("fraud"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
        mockMvc.perform(get(projectsOf(bob.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void newestMembershipComesFirst() throws Exception {
        Project first = projectRepository.save(new Project("first", null));
        Project second = projectRepository.save(new Project("second", null));
        membershipRepository.saveAndFlush(new ProjectMembership(first, alice, ProjectRole.OWNER));
        jdbcTemplate.update("UPDATE project_memberships SET joined_at = joined_at - INTERVAL '1 hour'");
        membershipRepository.saveAndFlush(new ProjectMembership(second, alice, ProjectRole.EDITOR));

        mockMvc.perform(get(projectsOf(alice.getId())))
                .andExpect(jsonPath("$[0].name").value("second"))
                .andExpect(jsonPath("$[1].name").value("first"));
    }

    @Test
    void pageSizeIsCappedAtFifty() throws Exception {
        for (int i = 0; i < 51; i++) {
            Project project = projectRepository.save(new Project("p" + i, null));
            membershipRepository.save(new ProjectMembership(project, alice, ProjectRole.OWNER));
        }

        mockMvc.perform(get(projectsOf(alice.getId())).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get(projectsOf(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void malformedUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-uuid/projects")).andExpect(status().isBadRequest());
    }

    private static String projectsOf(UUID userId) {
        return "/api/v1/users/" + userId + "/projects";
    }
}
