package ru.itmo.highload_ml.project.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {
}
