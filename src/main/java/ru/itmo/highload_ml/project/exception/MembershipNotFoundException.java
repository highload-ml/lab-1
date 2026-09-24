package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class MembershipNotFoundException extends NotFoundException {

    public MembershipNotFoundException(UUID projectId, UUID userId) {
        super("User %s is not a member of project %s".formatted(userId, projectId));
    }
}
