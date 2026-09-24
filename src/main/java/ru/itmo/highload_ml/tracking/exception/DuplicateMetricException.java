package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class DuplicateMetricException extends ConflictException {

    public DuplicateMetricException(UUID runId, String name, long step) {
        super("Metric '%s' at step %d already exists in run %s".formatted(name, step, runId));
    }

    public DuplicateMetricException(UUID runId) {
        super("A metric with the same name and step already exists in run %s".formatted(runId));
    }
}
