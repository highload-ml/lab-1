package ru.itmo.highload_ml.registry.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.port.ExperimentAccessPort;
import ru.itmo.highload_ml.project.port.ProjectAccessPort;
import ru.itmo.highload_ml.registry.api.dto.ModelVersionResponse;
import ru.itmo.highload_ml.registry.api.dto.RegisterModelVersionRequest;
import ru.itmo.highload_ml.registry.exception.InvalidModelArtifactException;
import ru.itmo.highload_ml.registry.exception.InvalidModelVersionTransitionException;
import ru.itmo.highload_ml.registry.exception.ModelArtifactAlreadyRegisteredException;
import ru.itmo.highload_ml.registry.exception.ModelVersionNotFoundException;
import ru.itmo.highload_ml.registry.mapper.ModelVersionMapper;
import ru.itmo.highload_ml.registry.model.ModelVersion;
import ru.itmo.highload_ml.registry.model.ModelVersionState;
import ru.itmo.highload_ml.registry.repository.ModelVersionRepository;
import ru.itmo.highload_ml.registry.repository.RegistryVersionCounter;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.port.ArtifactDetails;
import ru.itmo.highload_ml.tracking.port.ArtifactLookupPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ModelVersionService {

    private static final String ARTIFACT_UNIQUE_CONSTRAINT = "uk_model_versions_artifact";

    private final ProjectAccessPort projectAccessPort;
    private final ExperimentAccessPort experimentAccessPort;
    private final ArtifactLookupPort artifactLookupPort;
    private final ModelVersionRepository repository;
    private final RegistryVersionCounter counter;
    private final ModelVersionMapper mapper;

    public ModelVersionService(ProjectAccessPort projectAccessPort, ExperimentAccessPort experimentAccessPort,
                               ArtifactLookupPort artifactLookupPort, ModelVersionRepository repository,
                               RegistryVersionCounter counter, ModelVersionMapper mapper) {
        this.projectAccessPort = projectAccessPort;
        this.experimentAccessPort = experimentAccessPort;
        this.artifactLookupPort = artifactLookupPort;
        this.repository = repository;
        this.counter = counter;
        this.mapper = mapper;
    }

    /** The counter upsert serializes registrations in a project; the insert and counter advance commit together. */
    @Transactional
    public ModelVersionResponse register(UUID projectId, RegisterModelVersionRequest request) {
        projectAccessPort.requireMember(projectId, request.userId());
        ArtifactDetails artifact = artifactLookupPort.getDetails(request.artifactId());
        if (artifact.type() != ArtifactType.MODEL) {
            throw new InvalidModelArtifactException(request.artifactId(), "artifact type must be MODEL");
        }
        if (artifact.runStatus() != RunStatus.COMPLETED) {
            throw new InvalidModelArtifactException(request.artifactId(), "source run must be COMPLETED");
        }
        if (!experimentAccessPort.getProjectId(artifact.experimentId()).equals(projectId)) {
            throw new InvalidModelArtifactException(request.artifactId(), "artifact belongs to another project");
        }

        long version = counter.allocate(projectId);
        if (repository.existsByArtifactId(request.artifactId())) {
            throw new ModelArtifactAlreadyRegisteredException(request.artifactId());
        }
        try {
            return mapper.toResponse(repository.saveAndFlush(
                    new ModelVersion(projectId, request.artifactId(), version)));
        } catch (DataIntegrityViolationException e) {
            if (hasConstraint(e, ARTIFACT_UNIQUE_CONSTRAINT)) {
                throw new ModelArtifactAlreadyRegisteredException(request.artifactId());
            }
            throw e;
        }
    }

    public ModelVersionResponse getById(UUID projectId, UUID versionId) {
        projectAccessPort.requireExists(projectId);
        return mapper.toResponse(repository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ModelVersionNotFoundException(versionId)));
    }

    public Page<ModelVersionResponse> findAll(UUID projectId, Pageable pageable) {
        projectAccessPort.requireExists(projectId);
        return repository.findByProjectId(projectId, pageable).map(mapper::toResponse);
    }

    public ModelVersionResponse getProduction(UUID projectId) {
        projectAccessPort.requireExists(projectId);
        return mapper.toResponse(repository.findByProjectIdAndState(projectId, ModelVersionState.PRODUCTION)
                .orElseThrow(() -> new ModelVersionNotFoundException(projectId, "production")));
    }

    /** Locks the version row, so concurrent stage/promote calls cannot skip a state transition. */
    @Transactional
    public ModelVersionResponse stage(UUID projectId, UUID versionId, UUID userId) {
        projectAccessPort.requireMember(projectId, userId);
        ModelVersion version = lockVersion(projectId, versionId);
        version.stage();
        repository.flush();
        return mapper.toResponse(version);
    }

    /** Project lock serializes promotions; archiving and promotion are one atomic transaction. */
    @Transactional
    public ModelVersionResponse promoteToProduction(UUID projectId, UUID versionId, UUID userId) {
        projectAccessPort.requireMember(projectId, userId);
        if (!repository.existsByIdAndProjectId(versionId, projectId)) {
            throw new ModelVersionNotFoundException(versionId);
        }
        // Every registered version has a counter row. Lock it before reading production or the target.
        counter.lock(projectId);
        ModelVersion target = lockVersion(projectId, versionId);
        if (target.getState() != ModelVersionState.STAGING) {
            throw new InvalidModelVersionTransitionException(
                    versionId, target.getState(), ModelVersionState.PRODUCTION);
        }

        repository.findByProjectIdAndState(projectId, ModelVersionState.PRODUCTION).ifPresent(current -> {
            current.archive();
            // PostgreSQL's partial unique index is checked immediately: release the old slot first.
            repository.flush();
        });

        target.promote(Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository.flush();
        return mapper.toResponse(target);
    }

    private ModelVersion lockVersion(UUID projectId, UUID versionId) {
        return repository.findByIdAndProjectIdForUpdate(versionId, projectId)
                .orElseThrow(() -> new ModelVersionNotFoundException(versionId));
    }

    private static boolean hasConstraint(Throwable error, String constraintName) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
