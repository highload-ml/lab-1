package ru.itmo.highload_ml.registry.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.highload_ml.registry.api.dto.ModelVersionResponse;
import ru.itmo.highload_ml.registry.model.ModelVersion;

@Component
public class ModelVersionMapper {

    public ModelVersionResponse toResponse(ModelVersion version) {
        return new ModelVersionResponse(version.getId(), version.getProjectId(), version.getArtifactId(),
                version.getVersion(), version.getState(), version.getCreatedAt(), version.getPromotedAt());
    }
}
