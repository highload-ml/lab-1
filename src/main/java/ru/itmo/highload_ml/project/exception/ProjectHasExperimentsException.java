package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class ProjectHasExperimentsException extends ConflictException {

    public ProjectHasExperimentsException(UUID projectId) {
        super("Project %s still has experiments".formatted(projectId));
    }
}
