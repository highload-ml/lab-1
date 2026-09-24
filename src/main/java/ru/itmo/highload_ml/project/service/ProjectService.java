package ru.itmo.highload_ml.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateProjectRequest;
import ru.itmo.highload_ml.project.api.dto.ProjectResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateProjectRequest;
import ru.itmo.highload_ml.project.exception.ProjectHasExperimentsException;
import ru.itmo.highload_ml.project.exception.ProjectNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.mapper.ProjectMapper;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;
    private final ExperimentRepository experimentRepository;

    /**
     * The project and its initial OWNER membership must commit or roll back together.
     * Locking the owner row also serializes this operation with user deletion.
     */
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        User owner = userRepository.findByIdForUpdate(request.ownerId())
                .orElseThrow(() -> new UserNotFoundException(request.ownerId()));

        requireNameFree(request.name());
        Project project;
        try {
            project = projectRepository.saveAndFlush(new Project(request.name(), request.description()));
        } catch (DataIntegrityViolationException e) {
            throw new ProjectNameAlreadyTakenException(request.name());
        }
        membershipRepository.saveAndFlush(new ProjectMembership(project, owner, ProjectRole.OWNER));
        return projectMapper.toResponse(project);
    }

    public ProjectResponse getById(UUID id) {
        return projectMapper.toResponse(findProject(id));
    }

    public Page<ProjectResponse> findAll(Pageable pageable) {
        return projectRepository.findAll(pageable).map(projectMapper::toResponse);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        Project project = findProject(id);
        if (!project.getName().equals(request.name())) {
            requireNameFree(request.name());
            project.setName(request.name());
        }
        project.setDescription(request.description());
        try {
            projectRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProjectNameAlreadyTakenException(request.name());
        }
        return projectMapper.toResponse(project);
    }

    /**
     * Experiments prevent deletion. Otherwise memberships and the project are removed atomically;
     * the membership FK also cascades as a database-level safety net.
     */
    @Transactional
    public void delete(UUID id) {
        projectRepository.findByIdForUpdate(id).orElseThrow(() -> new ProjectNotFoundException(id));
        if (experimentRepository.existsByProject_Id(id)) {
            throw new ProjectHasExperimentsException(id);
        }
        membershipRepository.deleteAllByProjectId(id);
        projectRepository.deleteById(id);
    }

    private Project findProject(UUID id) {
        return projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private void requireNameFree(String name) {
        if (projectRepository.existsByName(name)) {
            throw new ProjectNameAlreadyTakenException(name);
        }
    }
}
