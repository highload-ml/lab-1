package ru.itmo.highload_ml.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void providesCommonApiMetadata() {
        OpenAPI openApi = new OpenApiConfig().highloadMlOpenApi();

        assertThat(openApi.getInfo()).isNotNull();
        assertThat(openApi.getInfo().getTitle()).isEqualTo("Experiment Tracking Platform API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openApi.getInfo().getDescription())
                .isEqualTo("REST API for projects, experiment tracking and model registry");
    }
}
