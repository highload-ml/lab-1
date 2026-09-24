package ru.itmo.highload_ml.registry.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.registry.RegistryModuleIntegrationTest;
import ru.itmo.highload_ml.tracking.model.ArtifactType;

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
class ModelVersionControllerIntegrationTest extends RegistryModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void registersStagesPromotesAndListsVersions() throws Exception {
        Fixture fixture = createFixture();
        String collection = collection(fixture.projectId());
        UUID firstArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(fixture.userId(), firstArtifact)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(
                        ".*/api/v1/projects/[0-9a-f-]{36}/model-versions/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.state").value("NEW"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactId").value(firstArtifact.toString()));
        mockMvc.perform(get(collection + "/production")).andExpect(status().isNotFound());

        mockMvc.perform(post(location + "/stage").contentType(MediaType.APPLICATION_JSON)
                        .content(action(fixture.userId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("STAGING"));
        mockMvc.perform(post(location + "/promote").contentType(MediaType.APPLICATION_JSON)
                        .content(action(fixture.userId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PRODUCTION"))
                .andExpect(jsonPath("$.promotedAt").exists());
        mockMvc.perform(get(collection + "/production")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        UUID secondArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(fixture.userId(), secondArtifact)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get(collection).param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].version").value(2));
        mockMvc.perform(get(collection).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    void returnsValidationAndDomainErrorsAsProblemDetails() throws Exception {
        Fixture fixture = createFixture();
        String collection = collection(fixture.projectId());
        UUID artifactId = createArtifact(fixture, ArtifactType.MODEL, true);

        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.userId").exists())
                .andExpect(jsonPath("$.validationErrors.artifactId").exists());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(UUID.randomUUID(), artifactId)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(fixture.userId(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(fixture.userId(), createArtifact(fixture, ArtifactType.LOG, true))))
                .andExpect(status().isUnprocessableEntity());
        String location = mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(fixture.userId(), artifactId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mockMvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                        .content(register(fixture.userId(), artifactId)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(location + "/promote").contentType(MediaType.APPLICATION_JSON)
                        .content(action(fixture.userId())))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post(location + "/stage").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.userId").exists());
        mockMvc.perform(get(collection + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get(collection).param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(collection(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    private static String collection(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/model-versions";
    }

    private static String register(UUID userId, UUID artifactId) {
        return "{\"userId\":\"" + userId + "\",\"artifactId\":\"" + artifactId + "\"}";
    }

    private static String action(UUID userId) {
        return "{\"userId\":\"" + userId + "\"}";
    }
}
