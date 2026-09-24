package ru.itmo.highload_ml.tracking.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.tracking.api.dto.ArtifactResponse;
import ru.itmo.highload_ml.tracking.api.dto.CreateArtifactRequest;
import ru.itmo.highload_ml.tracking.exception.ArtifactNameAlreadyTakenException;
import ru.itmo.highload_ml.tracking.exception.ArtifactNotFoundException;
import ru.itmo.highload_ml.tracking.exception.RunNotAcceptingArtifactsException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.mapper.ArtifactMapper;
import ru.itmo.highload_ml.tracking.model.Artifact;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.ArtifactRepository;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ArtifactService {

    private static final String UNIQUE_NAME_CONSTRAINT = "uk_artifacts_run_name";

    private final RunRepository runRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactMapper mapper;

    public ArtifactService(RunRepository runRepository, ArtifactRepository artifactRepository, ArtifactMapper mapper) {
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.mapper = mapper;
    }

    /** The run lock serializes artifact registration with completion and failure transitions. */
    @Transactional
    public ArtifactResponse register(UUID runId, CreateArtifactRequest request) {
        Run run = runRepository.findByIdForUpdate(runId).orElseThrow(() -> new RunNotFoundException(runId));
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new RunNotAcceptingArtifactsException(runId, run.getStatus());
        }
        if (artifactRepository.existsByRun_IdAndName(runId, request.name())) {
            throw new ArtifactNameAlreadyTakenException(runId, request.name());
        }
        try {
            Artifact artifact = artifactRepository.saveAndFlush(new Artifact(
                    run, request.name(), request.type(), request.path(), request.sizeBytes()));
            return mapper.toResponse(artifact);
        } catch (DataIntegrityViolationException e) {
            if (hasConstraint(e, UNIQUE_NAME_CONSTRAINT)) {
                throw new ArtifactNameAlreadyTakenException(runId, request.name());
            }
            throw e;
        }
    }

    public ArtifactResponse getById(UUID runId, UUID artifactId) {
        requireRun(runId);
        return mapper.toResponse(artifactRepository.findByIdAndRun_Id(artifactId, runId)
                .orElseThrow(() -> new ArtifactNotFoundException(artifactId)));
    }

    public Page<ArtifactResponse> findAll(UUID runId, Pageable pageable) {
        requireRun(runId);
        return artifactRepository.findByRun_Id(runId, pageable).map(mapper::toResponse);
    }

    private void requireRun(UUID runId) {
        if (!runRepository.existsById(runId)) {
            throw new RunNotFoundException(runId);
        }
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
