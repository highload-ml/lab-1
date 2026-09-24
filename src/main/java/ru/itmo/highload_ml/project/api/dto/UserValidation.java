package ru.itmo.highload_ml.project.api.dto;

final class UserValidation {

    static final String NICKNAME_PATTERN = "^[A-Za-z0-9_.-]+$";
    static final String NICKNAME_MESSAGE = "may contain only latin letters, digits, '_', '.' and '-'";

    private UserValidation() {
    }
}
