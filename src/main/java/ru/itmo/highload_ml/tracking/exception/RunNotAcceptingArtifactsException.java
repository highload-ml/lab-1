package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;
import ru.itmo.highload_ml.tracking.model.RunStatus;

import java.util.UUID;

public class RunNotAcceptingArtifactsException extends BusinessRuleViolationException {

    public RunNotAcceptingArtifactsException(UUID runId, RunStatus status) {
        super("Cannot register artifacts for run %s in status %s; status must be RUNNING"
                .formatted(runId, status));
    }
}
