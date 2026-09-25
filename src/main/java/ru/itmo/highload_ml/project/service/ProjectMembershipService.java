package ru.itmo.highload_ml.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.AddMemberRequest;
import ru.itmo.highload_ml.project.api.dto.MemberResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateMemberRoleRequest;
import ru.itmo.highload_ml.project.exception.LastOwnerRemovalException;
import ru.itmo.highload_ml.project.exception.MembershipAlreadyExistsException;
import ru.itmo.highload_ml.project.exception.MembershipNotFoundException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.mapper.ProjectMapper;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectMembershipId;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectMembershipService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    @Transactional
    public MemberResponse addMember(UUID projectId, AddMemberRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));
        if (membershipRepository.existsById(new ProjectMembershipId(projectId, user.getId()))) {
            throw new MembershipAlreadyExistsException(projectId, user.getId());
        }
        try {
            // flush inside the try: a concurrent add of the same pair hits the composite PK and becomes 409
            return projectMapper.toMemberResponse(
                    membershipRepository.saveAndFlush(new ProjectMembership(project, user, request.role())));
        } catch (DataIntegrityViolationException e) {
            throw new MembershipAlreadyExistsException(projectId, user.getId());
        }
    }

    public MemberResponse getMember(UUID projectId, UUID userId) {
        return projectMapper.toMemberResponse(findMembership(projectId, userId));
    }

    public Page<MemberResponse> findMembers(UUID projectId, Pageable pageable) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return membershipRepository.findByIdProjectId(projectId, pageable).map(projectMapper::toMemberResponse);
    }

    /**
     * Invariant: a project that has an owner never loses its last one.
     * The owner count check and the role change run in one transaction, so a failed check leaves no partial write.
     */
    @Transactional
    public MemberResponse changeRole(UUID projectId, UUID userId, UpdateMemberRoleRequest request) {
        ProjectMembership membership = findMembership(projectId, userId);
        if (request.role() != ProjectRole.OWNER) {
            requireNotLastOwner(membership);
        }
        membership.setRole(request.role());
        return projectMapper.toMemberResponse(membership);
    }

    /**
     * Same invariant as {@link #changeRole}.
     */
    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        ProjectMembership membership = findMembership(projectId, userId);
        requireNotLastOwner(membership);
        membershipRepository.delete(membership);
    }

    private ProjectMembership findMembership(UUID projectId, UUID userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return membershipRepository.findWithUserById(new ProjectMembershipId(projectId, userId))
                .orElseThrow(() -> new MembershipNotFoundException(projectId, userId));
    }

    private void requireNotLastOwner(ProjectMembership membership) {
        UUID projectId = membership.getId().getProjectId();
        if (membership.getRole() == ProjectRole.OWNER
                && membershipRepository.countByIdProjectIdAndRole(projectId, ProjectRole.OWNER) <= 1) {
            throw new LastOwnerRemovalException(projectId);
        }
    }
}
