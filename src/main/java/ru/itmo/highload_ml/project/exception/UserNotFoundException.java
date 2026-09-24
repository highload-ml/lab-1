package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(UUID userId) {
        super("User %s not found".formatted(userId));
    }
}
