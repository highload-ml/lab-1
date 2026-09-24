package ru.itmo.highload_ml.project.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.itmo.highload_ml.project.model.ProjectRole;

import java.util.UUID;

public record AddMemberRequest(
        @NotNull
        UUID userId,

        @NotNull
        ProjectRole role
) {
}
