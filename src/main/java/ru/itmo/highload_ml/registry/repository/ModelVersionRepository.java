package ru.itmo.highload_ml.registry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.registry.model.ModelVersion;
import ru.itmo.highload_ml.registry.model.ModelVersionState;

import java.util.Optional;
import java.util.UUID;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {

    Optional<ModelVersion> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ModelVersion> findByProjectIdAndState(UUID projectId, ModelVersionState state);

    boolean existsByArtifactId(UUID artifactId);

    Page<ModelVersion> findByProjectId(UUID projectId, Pageable pageable);
}
