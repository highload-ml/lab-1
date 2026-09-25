package ru.itmo.highload_ml.project.api.dto;

import ru.itmo.highload_ml.project.model.ProjectRole;

import java.time.Instant;
import java.util.UUID;

/**
 * A project as seen by one of its members: project fields plus the member's role and join time.
 */
public record UserProjectResponse(
        UUID projectId,
        String name,
        String description,
        Instant createdAt,
        ProjectRole role,
        Instant joinedAt
) {
}
