package ru.itmo.highload_ml.tracking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.itmo.highload_ml.tracking.service.RunCursor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunCursorCodecTest {

    @Test
    void roundTripsCursorWithinItsExperiment() {
        UUID experimentId = UUID.randomUUID();
        RunCursor cursor = new RunCursor(Instant.parse("2026-09-25T00:00:00Z"), UUID.randomUUID());

        String encoded = RunCursorCodec.encode(experimentId, cursor);

        assertThat(encoded).doesNotContain("+", "/", "=");
        assertThat(RunCursorCodec.decode(encoded, experimentId)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedAndCrossExperimentCursors() {
        UUID experimentId = UUID.randomUUID();
        String encoded = RunCursorCodec.encode(experimentId,
                new RunCursor(Instant.parse("2026-09-25T00:00:00Z"), UUID.randomUUID()));

        assertThatThrownBy(() -> RunCursorCodec.decode(encoded, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> RunCursorCodec.decode("not-a-cursor", experimentId))
                .isInstanceOf(ResponseStatusException.class);
    }
}
