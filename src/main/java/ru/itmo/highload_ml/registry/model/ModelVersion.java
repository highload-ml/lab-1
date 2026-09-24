package ru.itmo.highload_ml.registry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import ru.itmo.highload_ml.registry.exception.InvalidModelVersionTransitionException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "model_versions")
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "artifact_id", nullable = false, updatable = false)
    private UUID artifactId;

    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ModelVersionState state = ModelVersionState.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    protected ModelVersion() {
    }

    public ModelVersion(UUID projectId, UUID artifactId, long version) {
        this.projectId = projectId;
        this.artifactId = artifactId;
        this.version = version;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void stage() {
        requireState(ModelVersionState.NEW, ModelVersionState.STAGING);
        state = ModelVersionState.STAGING;
    }

    public void promote(Instant at) {
        requireState(ModelVersionState.STAGING, ModelVersionState.PRODUCTION);
        state = ModelVersionState.PRODUCTION;
        promotedAt = at;
    }

    public void archive() {
        requireState(ModelVersionState.PRODUCTION, ModelVersionState.ARCHIVED);
        state = ModelVersionState.ARCHIVED;
    }

    private void requireState(ModelVersionState expected, ModelVersionState target) {
        if (state != expected) {
            throw new InvalidModelVersionTransitionException(id, state, target);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getArtifactId() {
        return artifactId;
    }

    public long getVersion() {
        return version;
    }

    public ModelVersionState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }
}
