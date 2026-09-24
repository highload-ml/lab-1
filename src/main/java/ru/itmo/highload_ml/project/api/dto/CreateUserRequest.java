package ru.itmo.highload_ml.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.itmo.highload_ml.project.model.UserRole;

public record CreateUserRequest(
        @Schema(example = "alice_ml")
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = UserValidation.NICKNAME_PATTERN, message = UserValidation.NICKNAME_MESSAGE)
        String nickname,

        @Schema(example = "s3cure-Passw0rd", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotNull
        UserRole role
) {
}
