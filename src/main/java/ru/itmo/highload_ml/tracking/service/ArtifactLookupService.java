package ru.itmo.highload_ml.tracking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.tracking.exception.ArtifactNotFoundException;
import ru.itmo.highload_ml.tracking.model.Artifact;
import ru.itmo.highload_ml.tracking.port.ArtifactDetails;
import ru.itmo.highload_ml.tracking.port.ArtifactLookupPort;
import ru.itmo.highload_ml.tracking.repository.ArtifactRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ArtifactLookupService implements ArtifactLookupPort {

    private final ArtifactRepository artifactRepository;

    public ArtifactLookupService(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @Override
    public ArtifactDetails getDetails(UUID artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(artifactId));
        return new ArtifactDetails(artifact.getId(), artifact.getRun().getExperimentId(),
                artifact.getType(), artifact.getRun().getStatus());
    }
}
