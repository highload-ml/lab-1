package ru.itmo.highload_ml.project.port;

import java.util.UUID;

/**
 * Public contract of the project module for other modules (tracking, registry).
 * Callers depend only on this interface and UUIDs, never on project JPA entities or repositories,
 * so it can become a Feign client when the monolith is split in lab 2.
 */
public interface ProjectAccessPort {

    /**
     * @throws ru.itmo.highload_ml.project.exception.ProjectNotFoundException   if the project does not exist (404)
     * @throws ru.itmo.highload_ml.project.exception.NotProjectMemberException if the user is not a member (422)
     */
    void requireMember(UUID projectId, UUID userId);
}
