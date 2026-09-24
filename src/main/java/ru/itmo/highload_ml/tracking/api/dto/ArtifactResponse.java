package ru.itmo.highload_ml.tracking.api.dto;

import ru.itmo.highload_ml.tracking.model.ArtifactType;

import java.time.Instant;
import java.util.UUID;

public record ArtifactResponse(
        UUID id,
        UUID runId,
        String name,
        ArtifactType type,
        String path,
        long sizeBytes,
        Instant createdAt
) {
}
