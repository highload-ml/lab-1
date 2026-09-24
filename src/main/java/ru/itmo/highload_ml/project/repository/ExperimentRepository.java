package ru.itmo.highload_ml.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.highload_ml.project.model.Experiment;

import java.util.Optional;
import java.util.UUID;

public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {

    boolean existsByProjectIdAndName(UUID projectId, String name);

    Page<Experiment> findByProjectId(UUID projectId, Pageable pageable);

    // An (experiment, tag) pair is the join table's primary key, so the join never duplicates an experiment
    Page<Experiment> findByProjectIdAndTagsId(UUID projectId, UUID tagId, Pageable pageable);

    @Query("select e.project.id from Experiment e where e.id = :id")
    Optional<UUID> findProjectIdById(@Param("id") UUID id);
}
