package ru.itmo.highload_ml.project.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TagResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {
}
