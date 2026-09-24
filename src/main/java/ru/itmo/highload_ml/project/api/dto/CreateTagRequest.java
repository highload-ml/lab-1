package ru.itmo.highload_ml.project.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(@NotBlank @Size(max = 100) String name) {
}
