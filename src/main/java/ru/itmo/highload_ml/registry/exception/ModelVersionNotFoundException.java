package ru.itmo.highload_ml.registry.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class ModelVersionNotFoundException extends NotFoundException {

    public ModelVersionNotFoundException(UUID id) {
        super("Model version %s not found".formatted(id));
    }

    public ModelVersionNotFoundException(UUID projectId, String state) {
        super("No %s model version in project %s".formatted(state, projectId));
    }
}
