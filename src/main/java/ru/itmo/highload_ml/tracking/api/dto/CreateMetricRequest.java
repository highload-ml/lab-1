package ru.itmo.highload_ml.tracking.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateMetricRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Digits(integer = 11, fraction = 8) BigDecimal value,
        @NotNull @PositiveOrZero Long step
) {
}
