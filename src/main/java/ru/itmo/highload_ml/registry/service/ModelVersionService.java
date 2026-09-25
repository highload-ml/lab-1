package ru.itmo.highload_ml.registry.service;

import lombok.RequiredArgsConstructor;
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
import ru.itmo.highload_ml.registry.model.RegistryVersionCounter;
import ru.itmo.highload_ml.registry.repository.RegistryVersionCounterRepository;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.port.ArtifactDetails;
import ru.itmo.highload_ml.tracking.port.ArtifactLookupPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ModelVersionService {

    private final ProjectAccessPort projectAccessPort;
    private final ExperimentAccessPort experimentAccessPort;
    private final ArtifactLookupPort artifactLookupPort;
    private final ModelVersionRepository repository;
    private final RegistryVersionCounterRepository counterRepository;
    private final ModelVersionMapper mapper;

<<<<<<< HEAD
    public ModelVersionService(ProjectAccessPort projectAccessPort, ExperimentAccessPort experimentAccessPort,
                               ArtifactLookupPort artifactLookupPort, ModelVersionRepository repository,
                               RegistryVersionCounterRepository counterRepository,
                               ModelVersionMapper mapper) {
        this.projectAccessPort = projectAccessPort;
        this.experimentAccessPort = experimentAccessPort;
        this.artifactLookupPort = artifactLookupPort;
        this.repository = repository;
        this.counterRepository = counterRepository;
        this.mapper = mapper;
    }

    /** The counter advance and the version insert commit or roll back together. */
=======
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
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

        if (repository.existsByArtifactId(request.artifactId())) {
            throw new ModelArtifactAlreadyRegisteredException(request.artifactId());
        }
<<<<<<< HEAD
        long version = allocateVersion(projectId);
        try {
            return mapper.toResponse(repository.saveAndFlush(
                    new ModelVersion(projectId, request.artifactId(), version)));
        } catch (DataIntegrityViolationException e) {
            if (hasConstraint(e, ARTIFACT_UNIQUE_CONSTRAINT)) {
                throw new ModelArtifactAlreadyRegisteredException(request.artifactId());
            }
            throw e;
        }
=======
        return mapper.toResponse(repository.saveAndFlush(
                new ModelVersion(projectId, request.artifactId(), version)));
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
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

    @Transactional
    public ModelVersionResponse stage(UUID projectId, UUID versionId, UUID userId) {
        projectAccessPort.requireMember(projectId, userId);
<<<<<<< HEAD
        ModelVersion version = findVersion(projectId, versionId);
=======
        ModelVersion version = requireVersion(projectId, versionId);
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
        version.stage();
        repository.flush();
        return mapper.toResponse(version);
    }

<<<<<<< HEAD
    /** Archiving the old production version and promoting the target are one atomic transaction. */
    @Transactional
    public ModelVersionResponse promoteToProduction(UUID projectId, UUID versionId, UUID userId) {
        projectAccessPort.requireMember(projectId, userId);
        ModelVersion target = findVersion(projectId, versionId);
=======
    /** Archiving the current version and promoting the target are one transaction. */
    @Transactional
    public ModelVersionResponse promoteToProduction(UUID projectId, UUID versionId, UUID userId) {
        projectAccessPort.requireMember(projectId, userId);
        ModelVersion target = requireVersion(projectId, versionId);
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
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

<<<<<<< HEAD
    private long allocateVersion(UUID projectId) {
        RegistryVersionCounter counter = counterRepository.findById(projectId)
                .orElseGet(() -> new RegistryVersionCounter(projectId));
        long version = counter.allocate();
        counterRepository.save(counter);
        return version;
    }

    private ModelVersion findVersion(UUID projectId, UUID versionId) {
=======
    private ModelVersion requireVersion(UUID projectId, UUID versionId) {
>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
        return repository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ModelVersionNotFoundException(versionId));
    }
}
