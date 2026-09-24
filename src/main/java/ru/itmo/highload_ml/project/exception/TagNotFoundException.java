package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class TagNotFoundException extends NotFoundException {

    public TagNotFoundException(UUID tagId) {
        super("Tag %s not found".formatted(tagId));
    }
}
