package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class ExperimentNotFoundException extends NotFoundException {

    public ExperimentNotFoundException(UUID experimentId) {
        super("Experiment %s not found".formatted(experimentId));
    }
}
