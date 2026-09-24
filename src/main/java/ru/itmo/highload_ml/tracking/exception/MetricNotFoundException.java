package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class MetricNotFoundException extends NotFoundException {

    public MetricNotFoundException(UUID metricId) {
        super("Metric %s not found in this run".formatted(metricId));
    }
}
