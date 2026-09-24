package ru.itmo.highload_ml.project.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.api.dto.ExperimentResponse;
import ru.itmo.highload_ml.project.exception.ExperimentNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.mapper.ExperimentMapper;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExperimentService {

    private static final String UNIQUE_NAME_CONSTRAINT = "uk_experiments_project_name";

    private final ProjectRepository projectRepository;
    private final ExperimentRepository experimentRepository;
    private final TagRepository tagRepository;
    private final ExperimentMapper mapper;

    public ExperimentService(ProjectRepository projectRepository, ExperimentRepository experimentRepository,
                             TagRepository tagRepository, ExperimentMapper mapper) {
        this.projectRepository = projectRepository;
        this.experimentRepository = experimentRepository;
        this.tagRepository = tagRepository;
        this.mapper = mapper;
    }

    /** The project lock serializes creates and renames and coordinates with project deletion. */
    @Transactional
    public ExperimentResponse create(UUID projectId, CreateExperimentRequest request) {
        Project project = lockProject(projectId);
        requireNameFree(projectId, request.name());
        try {
            Experiment experiment = experimentRepository.saveAndFlush(
                    new Experiment(project, request.name(), request.description()));
            return mapper.toResponse(experiment);
        } catch (DataIntegrityViolationException e) {
            throw translateNameConflict(e, projectId, request.name());
        }
    }

    public ExperimentResponse getById(UUID projectId, UUID experimentId) {
        requireProject(projectId);
        return mapper.toResponse(experimentRepository.findByIdAndProjectIdWithTags(experimentId, projectId)
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId)));
    }

    public Page<ExperimentResponse> findAll(UUID projectId, UUID tagId, Pageable pageable) {
        requireProject(projectId);
        Page<Experiment> page = tagId == null
                ? experimentRepository.findByProject_Id(projectId, pageable)
                : experimentRepository.findByProjectAndTag(projectId, tagId, pageable);
        // Mapping happens inside this transaction; @BatchSize loads lazy tags in batches.
        return page.map(mapper::toResponse);
    }

    @Transactional
    public ExperimentResponse update(UUID projectId, UUID experimentId, CreateExperimentRequest request) {
        lockProject(projectId);
        Experiment experiment = lockExperiment(projectId, experimentId);
        if (!experiment.getName().equals(request.name())) {
            requireNameFree(projectId, request.name());
            experiment.setName(request.name());
        }
        experiment.setDescription(request.description());
        try {
            experimentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw translateNameConflict(e, projectId, request.name());
        }
        return mapper.toResponse(experiment);
    }

    @Transactional
    public ExperimentResponse addTag(UUID projectId, UUID experimentId, UUID tagId) {
        lockProject(projectId);
        Experiment experiment = lockExperiment(projectId, experimentId);
        Tag tag = requireTag(tagId);
        experiment.addTag(tag);
        experimentRepository.flush();
        return mapper.toResponse(experiment);
    }

    @Transactional
    public ExperimentResponse removeTag(UUID projectId, UUID experimentId, UUID tagId) {
        lockProject(projectId);
        Experiment experiment = lockExperiment(projectId, experimentId);
        Tag tag = requireTag(tagId);
        experiment.removeTag(tag);
        experimentRepository.flush();
        return mapper.toResponse(experiment);
    }

    @Transactional
    public void delete(UUID projectId, UUID experimentId) {
        lockProject(projectId);
        Experiment experiment = lockExperiment(projectId, experimentId);
        experimentRepository.delete(experiment);
        experimentRepository.flush();
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private Project lockProject(UUID projectId) {
        return projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private Experiment lockExperiment(UUID projectId, UUID experimentId) {
        return experimentRepository.findByIdAndProjectIdForUpdate(experimentId, projectId)
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));
    }

    private Tag requireTag(UUID tagId) {
        return tagRepository.findById(tagId).orElseThrow(() -> new TagNotFoundException(tagId));
    }

    private void requireNameFree(UUID projectId, String name) {
        if (experimentRepository.existsByProject_IdAndName(projectId, name)) {
            throw new ExperimentNameAlreadyTakenException(projectId, name);
        }
    }

    private RuntimeException translateNameConflict(DataIntegrityViolationException e, UUID projectId, String name) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_NAME_CONSTRAINT.equals(violation.getConstraintName())) {
                return new ExperimentNameAlreadyTakenException(projectId, name);
            }
        }
        return e;
    }
}
