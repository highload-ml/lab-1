package ru.itmo.highload_ml.project.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateProjectRequest;
import ru.itmo.highload_ml.project.api.dto.ProjectResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateProjectRequest;
import ru.itmo.highload_ml.project.exception.ProjectNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.mapper.ProjectMapper;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository,
            ProjectMapper projectMapper
    ) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        requireNameFree(request.name());
        try {
            return projectMapper.toResponse(
                    projectRepository.saveAndFlush(new Project(request.name(), request.description())));
        } catch (DataIntegrityViolationException e) {
            throw new ProjectNameAlreadyTakenException(request.name());
        }
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
     * Memberships are removed in the same transaction as the project, so a project never disappears
     * while its membership rows survive (the FK also cascades as a database-level safety net).
     */
    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException(id);
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
