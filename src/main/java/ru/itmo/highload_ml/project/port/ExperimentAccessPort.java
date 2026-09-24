package ru.itmo.highload_ml.project.port;

import java.util.UUID;

/**
 * Public project-module contract for tracking and registry. Other modules use UUIDs,
 * not project JPA entities or repositories; this boundary can become a remote client later.
 */
public interface ExperimentAccessPort {

    /** @throws ru.itmo.highload_ml.project.exception.ExperimentNotFoundException if absent (404) */
    void requireExists(UUID experimentId);

    /**
     * @return owning project id
     * @throws ru.itmo.highload_ml.project.exception.ExperimentNotFoundException if absent (404)
     */
    UUID getProjectId(UUID experimentId);
}
