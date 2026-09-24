package ru.itmo.highload_ml.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.itmo.highload_ml.project.model.UserRole;

public record UpdateUserRequest(
        @Schema(example = "alice_ml")
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = UserValidation.NICKNAME_PATTERN, message = UserValidation.NICKNAME_MESSAGE)
        String nickname,

        @NotNull
        UserRole role
) {
}
