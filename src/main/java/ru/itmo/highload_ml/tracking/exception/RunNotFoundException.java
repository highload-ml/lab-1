package ru.itmo.highload_ml.tracking.exception;

import ru.itmo.highload_ml.shared.exception.NotFoundException;

import java.util.UUID;

public class RunNotFoundException extends NotFoundException {

    public RunNotFoundException(UUID runId) {
        super("Run %s not found".formatted(runId));
    }
}
