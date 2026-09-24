package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class ExperimentNameAlreadyTakenException extends ConflictException {

    public ExperimentNameAlreadyTakenException(UUID projectId, String name) {
        super("Experiment name '%s' is already taken in project %s".formatted(name, projectId));
    }
}
