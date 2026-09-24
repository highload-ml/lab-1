package ru.itmo.highload_ml.project.api.dto;

import ru.itmo.highload_ml.project.model.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String nickname,
        UserRole role,
        Instant createdAt
) {
}
