package ru.itmo.highload_ml.project.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.itmo.highload_ml.project.model.ProjectRole;

public record UpdateMemberRoleRequest(
        @NotNull
        ProjectRole role
) {
}
