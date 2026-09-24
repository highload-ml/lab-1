package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class ExperimentNameAlreadyTakenException extends ConflictException {
    public ExperimentNameAlreadyTakenException(UUID projectId, String name) {
        super("Experiment '%s' already exists in project %s".formatted(name, projectId));
    }
}
