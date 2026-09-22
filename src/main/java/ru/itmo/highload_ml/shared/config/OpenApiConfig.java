package ru.itmo.highload_ml.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI highloadMlOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Experiment Tracking Platform API")
                        .version("v1")
                        .description("REST API for projects, experiment tracking and model registry"));
    }
}
