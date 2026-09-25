package ru.itmo.highload_ml.tracking.service;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ArtifactService {

    private final RunRepository runRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactMapper mapper;

    @Transactional
    public ArtifactResponse register(UUID runId, CreateArtifactRequest request) {
        Run run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new RunNotAcceptingArtifactsException(runId, run.getStatus());
        }
        if (artifactRepository.existsByRun_IdAndName(runId, request.name())) {
            throw new ArtifactNameAlreadyTakenException(runId, request.name());
        }
        Artifact artifact = artifactRepository.saveAndFlush(new Artifact(
                run, request.name(), request.type(), request.path(), request.sizeBytes()));
        return mapper.toResponse(artifact);
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

}
