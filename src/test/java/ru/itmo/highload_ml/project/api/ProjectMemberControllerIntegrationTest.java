package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectMemberControllerIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private User alice;
    private User bob;
    private String members;

    @BeforeEach
    void setUp() {
        project = projectRepository.save(new Project("fraud", null));
        alice = userRepository.save(new User("alice", "hash", UserRole.ML_ENGINEER));
        bob = userRepository.save(new User("bob", "hash", UserRole.REVIEWER));
        members = "/api/v1/projects/" + project.getId() + "/members";
    }

    @Test
    void addMemberReturns201WithLocation() throws Exception {
        addMember(alice, "OWNER")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(members + "/" + alice.getId())))
                .andExpect(jsonPath("$.userId").value(alice.getId().toString()))
                .andExpect(jsonPath("$.nickname").value("alice"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.joinedAt").exists());

        mockMvc.perform(get(members + "/" + alice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void addingSameUserTwiceReturns409() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());

        addMember(alice, "VIEWER").andExpect(status().isConflict());
    }

    @Test
    void addingUnknownUserReturns404() throws Exception {
        mockMvc.perform(post(members).contentType(MediaType.APPLICATION_JSON)
                        .content(memberJson(UUID.randomUUID(), "VIEWER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingToUnknownProjectReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberJson(alice.getId(), "VIEWER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingWithoutRoleReturns400() throws Exception {
        mockMvc.perform(post(members).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "%s"}
                                """.formatted(alice.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.role").exists());
    }

    @Test
    void listReturnsMembersWithTotalCountHeader() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());
        addMember(bob, "VIEWER").andExpect(status().isCreated());

        mockMvc.perform(get(members).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nickname").value("alice"));
    }

    @Test
    void listOfUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/members"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getNonMemberReturns404() throws Exception {
        mockMvc.perform(get(members + "/" + bob.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeRoleUpdatesMember() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());
        addMember(bob, "VIEWER").andExpect(status().isCreated());

        changeRole(bob, "EDITOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void demotingLastOwnerReturns422() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());

        changeRole(alice, "EDITOR")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void removingLastOwnerReturns422ButSecondOwnerCanLeave() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());
        addMember(bob, "OWNER").andExpect(status().isCreated());

        mockMvc.perform(delete(members + "/" + bob.getId())).andExpect(status().isNoContent());
        mockMvc.perform(delete(members + "/" + alice.getId())).andExpect(status().isUnprocessableContent());
    }

    @Test
    void removeMemberReturns204ThenMemberIsGone() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());
        addMember(bob, "VIEWER").andExpect(status().isCreated());

        mockMvc.perform(delete(members + "/" + bob.getId())).andExpect(status().isNoContent());
        mockMvc.perform(get(members + "/" + bob.getId())).andExpect(status().isNotFound());
        mockMvc.perform(delete(members + "/" + bob.getId())).andExpect(status().isNotFound());
    }

    @Test
    void deletingUserWhoIsMemberReturns409() throws Exception {
        addMember(alice, "OWNER").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/users/" + alice.getId())).andExpect(status().isConflict());
    }

    private ResultActions addMember(User user, String role) throws Exception {
        return mockMvc.perform(post(members).contentType(MediaType.APPLICATION_JSON)
                .content(memberJson(user.getId(), role)));
    }

    private ResultActions changeRole(User user, String role) throws Exception {
        return mockMvc.perform(put(members + "/" + user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role": "%s"}
                        """.formatted(role)));
    }

    private static String memberJson(UUID userId, String role) {
        return """
                {"userId": "%s", "role": "%s"}
                """.formatted(userId, role);
    }
}
