package ru.itmo.highload_ml.registry.exception;

import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;

import java.util.UUID;

public class InvalidModelArtifactException extends BusinessRuleViolationException {

    public InvalidModelArtifactException(UUID artifactId, String reason) {
        super("Artifact %s cannot be registered as a model: %s".formatted(artifactId, reason));
    }
}
