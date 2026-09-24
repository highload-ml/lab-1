package ru.itmo.highload_ml.tracking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.highload_ml.tracking.model.Artifact;

import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    boolean existsByRun_IdAndName(UUID runId, String name);

    Optional<Artifact> findByIdAndRun_Id(UUID artifactId, UUID runId);

    Page<Artifact> findByRun_Id(UUID runId, Pageable pageable);
}
