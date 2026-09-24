package ru.itmo.highload_ml.tracking.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable keyset position in a descending (createdAt, id) run listing. */
public record RunCursor(Instant createdAt, UUID id) {

    public RunCursor {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(id, "id");
    }
}
