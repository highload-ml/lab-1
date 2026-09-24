package ru.itmo.highload_ml.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @Schema(example = "fraud-detection")
        @NotBlank
        @Size(max = 100)
        String name,

        @Schema(example = "Card transaction fraud models")
        @Size(max = 1000)
        String description
) {
}
