package ru.itmo.highload_ml.shared.exception;

/**
 * Base class for business errors raised by module services.
 * Free of web dependencies so modules stay transport-agnostic; HTTP mapping lives in ApiExceptionHandler.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
