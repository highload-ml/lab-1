package ru.itmo.highload_ml.tracking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRunRequest(
        @NotNull UUID authorId,
        @NotBlank @Size(max = 100) String name
) {
}
