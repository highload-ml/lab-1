package ru.itmo.highload_ml.project.exception;

import ru.itmo.highload_ml.shared.exception.ConflictException;

public class NicknameAlreadyTakenException extends ConflictException {

    public NicknameAlreadyTakenException(String nickname) {
        super("Nickname '%s' is already taken".formatted(nickname));
    }
}
