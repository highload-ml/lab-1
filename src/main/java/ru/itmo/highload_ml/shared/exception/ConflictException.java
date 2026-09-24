package ru.itmo.highload_ml.shared.exception;

/**
 * Operation conflicts with the current state of a resource (e.g. uniqueness). Mapped to HTTP 409.
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
