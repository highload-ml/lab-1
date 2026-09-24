package ru.itmo.highload_ml.project.port;

import java.util.UUID;

/**
 * Public contract of the project module for experiments, used by the tracking module to attach runs.
 * Like {@link ProjectAccessPort}, callers see only UUIDs, never the Experiment entity or its repository.
 */
public interface ExperimentAccessPort {

    /**
     * @throws ru.itmo.highload_ml.project.exception.ExperimentNotFoundException if the experiment does not exist (404)
     */
    void requireExists(UUID experimentId);

    /**
     * @return id of the project the experiment belongs to
     * @throws ru.itmo.highload_ml.project.exception.ExperimentNotFoundException if the experiment does not exist (404)
     */
    UUID getProjectId(UUID experimentId);
}
