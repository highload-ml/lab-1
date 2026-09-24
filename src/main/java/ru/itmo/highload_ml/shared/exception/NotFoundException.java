package ru.itmo.highload_ml.shared.exception;

/**
 * Requested resource does not exist. Mapped to HTTP 404.
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
