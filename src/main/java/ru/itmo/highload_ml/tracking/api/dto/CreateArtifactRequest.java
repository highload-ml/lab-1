package ru.itmo.highload_ml.tracking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.itmo.highload_ml.tracking.model.ArtifactType;

public record CreateArtifactRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull ArtifactType type,
        @NotBlank @Size(max = 2048) String path,
        @NotNull @PositiveOrZero Long sizeBytes
) {
}
