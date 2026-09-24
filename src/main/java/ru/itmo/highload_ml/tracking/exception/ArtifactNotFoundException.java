package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class ArtifactNotFoundException extends NotFoundException {

    public ArtifactNotFoundException(UUID artifactId) {
        super("Artifact %s not found in this run".formatted(artifactId));
    }
}
