package ru.itmo.highload_ml.tracking.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MetricResponse(
        UUID id,
        UUID runId,
        String name,
        BigDecimal value,
        long step,
        Instant recordedAt
) {
}
