package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

public class ProjectNameAlreadyTakenException extends ConflictException {

    public ProjectNameAlreadyTakenException(String name) {
        super("Project name '%s' is already taken".formatted(name));
    }
}
