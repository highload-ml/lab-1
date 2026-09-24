package ru.itmo.highload_ml.project.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Tag;
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
class TagControllerIntegrationTest extends ProjectModuleIntegrationTest {

    private static final String TAGS = "/api/v1/tags";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagRepository tagRepository;

    @Test
    void createReturns201WithLocation() throws Exception {
        mockMvc.perform(post(TAGS).contentType(MediaType.APPLICATION_JSON).content(tagJson("baseline")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/tags/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.name").value("baseline"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createWithDuplicateNameReturns409() throws Exception {
        createTag("baseline");

        mockMvc.perform(post(TAGS).contentType(MediaType.APPLICATION_JSON).content(tagJson("baseline")))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post(TAGS).contentType(MediaType.APPLICATION_JSON).content(tagJson(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void getReturnsTagAndUnknownReturns404() throws Exception {
        String location = createTag("baseline");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("baseline"));
        mockMvc.perform(get(TAGS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsPageSortedByNameWithTotalCountHeader() throws Exception {
        createTag("c");
        createTag("a");
        createTag("b");

        mockMvc.perform(get(TAGS).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("a"))
                .andExpect(jsonPath("$[1].name").value("b"));
    }

    @Test
    void listCapsPageSizeAtFifty() throws Exception {
        for (int i = 0; i < 51; i++) {
            tagRepository.save(new Tag("t" + i, null));
        }

        mockMvc.perform(get(TAGS).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "51"))
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void updateChangesTagAndRejectsTakenName() throws Exception {
        createTag("baseline");
        String location = createTag("xgboost");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(tagJson("lightgbm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("lightgbm"));
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(tagJson("baseline")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204ThenUnknownReturns404() throws Exception {
        String location = createTag("baseline");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(delete(location)).andExpect(status().isNotFound());
        assertThat(tagRepository.count()).isZero();
    }

    private String createTag(String name) throws Exception {
        String location = mockMvc.perform(post(TAGS).contentType(MediaType.APPLICATION_JSON).content(tagJson(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return location;
    }

    private static String tagJson(String name) {
        return """
                {"name": "%s"}
                """.formatted(name);
    }
}
