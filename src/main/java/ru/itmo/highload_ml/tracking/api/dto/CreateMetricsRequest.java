package ru.itmo.highload_ml.tracking.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMetricsRequest(
        @NotEmpty @Size(max = 100) List<@Valid CreateMetricRequest> metrics
) {
}
