package ru.itmo.highload_ml.registry.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ModelActionRequest(@NotNull UUID userId) {
}
