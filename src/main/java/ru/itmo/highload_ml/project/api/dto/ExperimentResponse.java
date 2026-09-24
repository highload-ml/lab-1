package ru.itmo.highload_ml.project.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        Instant createdAt,
        List<TagResponse> tags
) {}

