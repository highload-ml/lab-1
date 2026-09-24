package ru.itmo.highload_ml.registry.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class ModelArtifactAlreadyRegisteredException extends ConflictException {

    public ModelArtifactAlreadyRegisteredException(UUID artifactId) {
        super("Artifact %s is already registered as a model version".formatted(artifactId));
    }
}
