package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class ArtifactNameAlreadyTakenException extends ConflictException {

    public ArtifactNameAlreadyTakenException(UUID runId, String name) {
        super("Artifact '%s' already exists in run %s".formatted(name, runId));
    }
}
