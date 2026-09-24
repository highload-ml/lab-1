package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class ProjectNotFoundException extends NotFoundException {

    public ProjectNotFoundException(UUID projectId) {
        super("Project %s not found".formatted(projectId));
    }
}
