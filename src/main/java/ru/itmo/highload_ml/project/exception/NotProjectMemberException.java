package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.BusinessRuleViolationException;

import java.util.UUID;

public class NotProjectMemberException extends BusinessRuleViolationException {

    public NotProjectMemberException(UUID projectId, UUID userId) {
        super("User %s is not a member of project %s".formatted(userId, projectId));
    }
}
