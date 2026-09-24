package ru.itmo.highload_ml.registry.exception;

import ru.itmo.highload_ml.registry.model.ModelVersionState;
import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;

import java.util.UUID;

public class InvalidModelVersionTransitionException extends BusinessRuleViolationException {

    public InvalidModelVersionTransitionException(UUID id, ModelVersionState current, ModelVersionState target) {
        super("Model version %s cannot transition from %s to %s".formatted(id, current, target));
    }
}
