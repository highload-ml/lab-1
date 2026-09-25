package ru.itmo.highload_ml.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    long countByIdProjectIdAndRole(UUID projectId, ProjectRole role);

    boolean existsByIdUserId(UUID userId);

    void deleteAllByIdProjectId(UUID projectId);
}
