package ru.itmo.highload_ml.registry.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterModelVersionRequest(@NotNull UUID userId, @NotNull UUID artifactId) {
}
