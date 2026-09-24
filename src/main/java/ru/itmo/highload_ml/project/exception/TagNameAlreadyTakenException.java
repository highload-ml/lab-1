package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

public class TagNameAlreadyTakenException extends ConflictException {

    public TagNameAlreadyTakenException(String name) {
        super("Tag '%s' already exists".formatted(name));
    }
}
