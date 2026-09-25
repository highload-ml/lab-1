package ru.itmo.highload_ml.registry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Next project-local model version number; one row per project. */
@Entity
@Table(name = "registry_version_counters")
public class RegistryVersionCounter {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "next_version", nullable = false)
    private long nextVersion;

    protected RegistryVersionCounter() {
    }

    public RegistryVersionCounter(UUID projectId) {
        this.projectId = projectId;
        this.nextVersion = 1;
    }

    /** Returns the current number and advances the counter. */
    public long allocate() {
        return nextVersion++;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public long getNextVersion() {
        return nextVersion;
    }
}
