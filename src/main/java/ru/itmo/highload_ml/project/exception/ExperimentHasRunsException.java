package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class ExperimentHasRunsException extends ConflictException {

    public ExperimentHasRunsException(UUID experimentId) {
        super("Experiment %s cannot be deleted while it has runs".formatted(experimentId));
    }
}
