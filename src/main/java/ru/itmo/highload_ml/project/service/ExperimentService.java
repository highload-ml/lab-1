package ru.itmo.highload_ml.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.api.dto.ExperimentResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateExperimentRequest;
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
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;
    private final ExperimentMapper experimentMapper;

    @Transactional
    public ExperimentResponse create(UUID projectId, CreateExperimentRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        requireNameFree(projectId, request.name());
        try {
            return experimentMapper.toResponse(experimentRepository.saveAndFlush(
                    new Experiment(project, request.name(), request.description())));
        } catch (DataIntegrityViolationException e) {
            throw new ExperimentNameAlreadyTakenException(projectId, request.name());
        }
    }

    public ExperimentResponse getById(UUID id) {
        return experimentMapper.toResponse(findExperiment(id));
    }

    /**
     * @param tagId optional filter; when null all experiments of the project are returned
     */
    public Page<ExperimentResponse> findByProject(UUID projectId, UUID tagId, Pageable pageable) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        Page<Experiment> page = tagId == null
                ? experimentRepository.findByProjectId(projectId, pageable)
                : experimentRepository.findByProjectIdAndTagsId(projectId, tagId, pageable);
        return page.map(experimentMapper::toResponse);
    }

    @Transactional
    public ExperimentResponse update(UUID id, UpdateExperimentRequest request) {
        Experiment experiment = findExperiment(id);
        UUID projectId = experiment.getProject().getId();
        if (!experiment.getName().equals(request.name())) {
            requireNameFree(projectId, request.name());
            experiment.setName(request.name());
        }
        experiment.setDescription(request.description());
        try {
            experimentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ExperimentNameAlreadyTakenException(projectId, request.name());
        }
        return experimentMapper.toResponse(experiment);
    }

    @Transactional
    public void delete(UUID id) {
        if (!experimentRepository.existsById(id)) {
            throw new ExperimentNotFoundException(id);
        }
        experimentRepository.deleteById(id);
    }

    /**
     * Idempotent: assigning a tag the experiment already has changes nothing.
     */
    @Transactional
    public void assignTag(UUID experimentId, UUID tagId) {
        findExperiment(experimentId).addTag(findTag(tagId));
    }

    /**
     * Idempotent for an existing tag that is not assigned; 404 only when the experiment or the tag does not exist.
     */
    @Transactional
    public void removeTag(UUID experimentId, UUID tagId) {
        findExperiment(experimentId).removeTag(findTag(tagId));
    }

    private Experiment findExperiment(UUID id) {
        return experimentRepository.findById(id).orElseThrow(() -> new ExperimentNotFoundException(id));
    }

    private Tag findTag(UUID id) {
        return tagRepository.findById(id).orElseThrow(() -> new TagNotFoundException(id));
    }

    private void requireNameFree(UUID projectId, String name) {
        if (experimentRepository.existsByProjectIdAndName(projectId, name)) {
            throw new ExperimentNameAlreadyTakenException(projectId, name);
        }
    }
}
