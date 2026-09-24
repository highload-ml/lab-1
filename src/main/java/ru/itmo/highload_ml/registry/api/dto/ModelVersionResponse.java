package ru.itmo.highload_ml.registry.api.dto;

import ru.itmo.highload_ml.registry.model.ModelVersionState;

import java.time.Instant;
import java.util.UUID;

public record ModelVersionResponse(
        UUID id,
        UUID projectId,
        UUID artifactId,
        long version,
        ModelVersionState state,
        Instant createdAt,
        Instant promotedAt
) {
}
