package ru.itmo.highload_ml.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.highload_ml.project.model.Experiment;

import java.util.Optional;
import java.util.UUID;

public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {

    boolean existsByProject_Id(UUID projectId);

    boolean existsByProject_IdAndName(UUID projectId, String name);

    Page<Experiment> findByProject_Id(UUID projectId, Pageable pageable);

    @EntityGraph(attributePaths = "tags")
    @Query("select e from Experiment e where e.id = :id and e.project.id = :projectId")
    Optional<Experiment> findByIdAndProjectIdWithTags(@Param("id") UUID id, @Param("projectId") UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Experiment e where e.id = :id and e.project.id = :projectId")
    Optional<Experiment> findByIdAndProjectIdForUpdate(@Param("id") UUID id, @Param("projectId") UUID projectId);

    @Query(
            value = """
        select e from Experiment e join e.tags t
        where e.project.id = :projectId and t.id = :tagId
        """,
            countQuery = """
        select count(e) from Experiment e join e.tags t
        where e.project.id = :projectId and t.id = :tagId
        """
    )
    Page<Experiment> findByProjectAndTag(
            @Param("projectId") UUID projectId, @Param("tagId") UUID tagId, Pageable pageable);
}
