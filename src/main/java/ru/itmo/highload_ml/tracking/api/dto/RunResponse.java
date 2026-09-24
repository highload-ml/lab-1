package ru.itmo.highload_ml.tracking.api.dto;

import ru.itmo.highload_ml.tracking.model.RunStatus;

import java.time.Instant;
import java.util.UUID;

public record RunResponse(
        UUID id,
        UUID experimentId,
        UUID authorId,
        String name,
        RunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
