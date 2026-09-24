package ru.itmo.highload_ml.shared.exception;

/**
 * Request is well-formed but violates a business rule (e.g. invalid state transition). Mapped to HTTP 422.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
