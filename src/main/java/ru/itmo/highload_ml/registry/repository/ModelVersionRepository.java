package ru.itmo.highload_ml.registry.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.highload_ml.registry.model.ModelVersion;
import ru.itmo.highload_ml.registry.model.ModelVersionState;

import java.util.Optional;
import java.util.UUID;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {

    Optional<ModelVersion> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ModelVersion v where v.id = :id and v.projectId = :projectId")
    Optional<ModelVersion> findByIdAndProjectIdForUpdate(@Param("id") UUID id,
                                                         @Param("projectId") UUID projectId);

    Optional<ModelVersion> findByProjectIdAndState(UUID projectId, ModelVersionState state);

    boolean existsByArtifactId(UUID artifactId);

    Page<ModelVersion> findByProjectId(UUID projectId, Pageable pageable);
}
