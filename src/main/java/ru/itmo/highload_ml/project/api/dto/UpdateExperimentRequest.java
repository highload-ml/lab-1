package ru.itmo.highload_ml.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateExperimentRequest(
        @Schema(example = "xgboost-baseline")
        @NotBlank
        @Size(max = 100)
        String name,

        @Schema(example = "Gradient boosting on raw transaction features")
        @Size(max = 1000)
        String description
) {
}
