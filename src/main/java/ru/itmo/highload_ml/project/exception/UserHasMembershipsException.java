package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

import java.util.UUID;

public class UserHasMembershipsException extends ConflictException {

    public UserHasMembershipsException(UUID userId) {
        super("User %s still belongs to projects and cannot be deleted".formatted(userId));
    }
}
