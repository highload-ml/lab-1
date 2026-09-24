package ru.itmo.highload_ml.tracking.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.tracking.api.dto.ArtifactResponse;
import ru.itmo.highload_ml.tracking.model.Artifact;

@Component
public class ArtifactMapper {

    public ArtifactResponse toResponse(Artifact artifact) {
        return new ArtifactResponse(
                artifact.getId(),
                artifact.getRun().getId(),
                artifact.getName(),
                artifact.getType(),
                artifact.getPath(),
                artifact.getSizeBytes(),
                artifact.getCreatedAt()
        );
    }
}
