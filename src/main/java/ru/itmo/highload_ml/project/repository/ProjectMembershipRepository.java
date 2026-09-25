package ru.itmo.highload_ml.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectMembershipId;
import ru.itmo.highload_ml.project.model.ProjectRole;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, ProjectMembershipId> {

    @EntityGraph(attributePaths = "user")
    Page<ProjectMembership> findByIdProjectId(UUID projectId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<ProjectMembership> findWithUserById(ProjectMembershipId id);

    // The project is fetched in the same query, so a page of memberships costs one select plus one count.
    @EntityGraph(attributePaths = "project")
    Page<ProjectMembership> findByIdUserId(UUID userId, Pageable pageable);

    long countByIdProjectIdAndRole(UUID projectId, ProjectRole role);

    boolean existsByIdUserId(UUID userId);

    @Modifying
    @Query("delete from ProjectMembership m where m.id.projectId = :projectId")
    void deleteAllByProjectId(@Param("projectId") UUID projectId);
}
