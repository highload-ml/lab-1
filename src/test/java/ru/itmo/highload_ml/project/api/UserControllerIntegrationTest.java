package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
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
class UserControllerIntegrationTest extends ProjectModuleIntegrationTest {

    private static final String USERS = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createReturns201WithLocationAndHidesPassword() throws Exception {
        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("alice", "password123", "ML_ENGINEER")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/users/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nickname").value("alice"))
                .andExpect(jsonPath("$.role").value("ML_ENGINEER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createWithoutPasswordReturns201AndStoresNoHash() throws Exception {
        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "alice", "role": "ML_ENGINEER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("alice"));

        assertThat(userRepository.findByNickname("alice")).get()
                .satisfies(user -> assertThat(user.getPasswordHash()).isNull());
    }

    @Test
    void getByNicknameReturnsUserOr404() throws Exception {
        createUser("alice", "REVIEWER");

        mockMvc.perform(get(USERS + "/by-nickname/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("alice"))
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(get(USERS + "/by-nickname/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User with nickname 'ghost' not found"));
    }

    @Test
    void createdUserIsReachableByLocation() throws Exception {
        String location = createUser("alice", "REVIEWER");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("alice"))
                .andExpect(jsonPath("$.role").value("REVIEWER"));
    }

    @Test
    void createWithDuplicateNicknameReturns409() throws Exception {
        createUser("alice", "ADMIN");

        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("alice", "password123", "REVIEWER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Nickname 'alice' is already taken"));
    }

    @Test
    void createWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("a b", "short", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.nickname").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void createWithUnknownRoleReturns400() throws Exception {
        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("alice", "password123", "ROOT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void getUnknownUserReturns404() throws Exception {
        mockMvc.perform(get(USERS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(get(USERS + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsPageWithTotalCountHeader() throws Exception {
        createUser("alice", "ADMIN");
        createUser("bob", "ML_ENGINEER");
        createUser("carol", "REVIEWER");

        mockMvc.perform(get(USERS).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nickname").value("alice"));

        mockMvc.perform(get(USERS).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nickname").value("carol"));
    }

    @Test
    void listCapsPageSizeAtFifty() throws Exception {
        // seeded via repository: hashing 55 passwords through the API is needlessly slow
        for (int i = 0; i < 55; i++) {
            userRepository.save(new User("user" + i, "hash", UserRole.ML_ENGINEER));
        }

        mockMvc.perform(get(USERS).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "55"))
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void listRejectsNegativePage() throws Exception {
        mockMvc.perform(get(USERS).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateChangesNicknameAndRole() throws Exception {
        String location = createUser("alice", "REVIEWER");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "alice2", "role": "ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("alice2"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateToTakenNicknameReturns409() throws Exception {
        createUser("alice", "ADMIN");
        String bob = createUser("bob", "REVIEWER");

        mockMvc.perform(put(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "alice", "role": "REVIEWER"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUnknownUserReturns404() throws Exception {
        mockMvc.perform(put(USERS + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "ghost", "role": "REVIEWER"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204ThenUserIsGone() throws Exception {
        String location = createUser("alice", "ADMIN");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
        mockMvc.perform(delete(location)).andExpect(status().isNotFound());
    }

    private String createUser(String nickname, String role) throws Exception {
        String location = mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(nickname, "password123", role)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        assertThat(location).isNotNull();
        return location;
    }

    private static String userJson(String nickname, String password, String role) {
        return """
                {"nickname": "%s", "password": "%s", "role": "%s"}
                """.formatted(nickname, password, role);
    }
}
