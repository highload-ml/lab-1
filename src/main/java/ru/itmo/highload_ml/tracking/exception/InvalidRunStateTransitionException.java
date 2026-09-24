package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;
import ru.itmo.highload_ml.tracking.model.RunStatus;

import java.util.UUID;

public class InvalidRunStateTransitionException extends BusinessRuleViolationException {

    public InvalidRunStateTransitionException(UUID runId, RunStatus current, RunStatus target) {
        super("Run %s cannot transition from %s to %s".formatted(runId, current, target));
    }
}
