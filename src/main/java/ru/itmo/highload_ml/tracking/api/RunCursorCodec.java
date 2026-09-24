package ru.itmo.highload_ml.tracking.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.itmo.highload_ml.tracking.service.RunCursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Encodes a keyset position as an opaque URL-safe token, not as a page offset. */
public final class RunCursorCodec {

    private RunCursorCodec() {
    }

    public static String encode(UUID experimentId, RunCursor cursor) {
        String value = experimentId + "|" + cursor.createdAt() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static RunCursor decode(String value, UUID experimentId) {
        if (value == null || value.isBlank() || value.length() > 192) {
            throw invalidCursor();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || !UUID.fromString(parts[0]).equals(experimentId)) {
                throw invalidCursor();
            }
            return new RunCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalidCursor();
        }
    }

    private static ResponseStatusException invalidCursor() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid run cursor");
    }
}
