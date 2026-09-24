package ru.itmo.highload_ml.project.api.dto;

import ru.itmo.highload_ml.project.model.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String nickname,
        ProjectRole role,
        Instant joinedAt
) {
}
