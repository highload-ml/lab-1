package ru.itmo.highload_ml.tracking.port;

import java.util.UUID;

/** Public tracking contract for consumers such as the model registry. */
public interface ArtifactLookupPort {

    /** @throws ru.itmo.highload_ml.tracking.exception.ArtifactNotFoundException if absent */
    ArtifactDetails getDetails(UUID artifactId);
}
