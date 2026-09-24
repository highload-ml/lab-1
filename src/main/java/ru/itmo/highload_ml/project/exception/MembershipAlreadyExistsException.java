package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class MembershipAlreadyExistsException extends ConflictException {

    public MembershipAlreadyExistsException(UUID projectId, UUID userId) {
        super("User %s is already a member of project %s".formatted(userId, projectId));
    }
}
