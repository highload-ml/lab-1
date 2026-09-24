package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;

import java.util.UUID;

public class LastOwnerRemovalException extends BusinessRuleViolationException {

    public LastOwnerRemovalException(UUID projectId) {
        super("Project %s must keep at least one owner".formatted(projectId));
    }
}
