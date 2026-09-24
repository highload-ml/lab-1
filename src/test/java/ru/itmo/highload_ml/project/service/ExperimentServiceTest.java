package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.api.dto.ExperimentResponse;
import ru.itmo.highload_ml.project.api.dto.TagResponse;
import ru.itmo.highload_ml.project.api.dto.UpdateExperimentRequest;
import ru.itmo.highload_ml.project.exception.ExperimentNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.mapper.ExperimentMapper;
import ru.itmo.highload_ml.project.mapper.TagMapper;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.Tag;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.itmo.highload_ml.project.TestEntities.experiment;
import static ru.itmo.highload_ml.project.TestEntities.project;
import static ru.itmo.highload_ml.project.TestEntities.tag;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TagRepository tagRepository;

    @Spy
    private ExperimentMapper experimentMapper = new ExperimentMapper(new TagMapper());

    @InjectMocks
    private ExperimentService experimentService;

    @Test
    void createSavesExperimentInProject() {
        Project project = project("fraud");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.existsByProjectIdAndName(project.getId(), "run-1")).thenReturn(false);
        when(experimentRepository.saveAndFlush(any(Experiment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentResponse response = experimentService.create(project.getId(), new CreateExperimentRequest("run-1", "d"));

        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.name()).isEqualTo("run-1");
        assertThat(response.tags()).isEmpty();
    }

    @Test
    void createInUnknownProjectThrowsNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.create(projectId, new CreateExperimentRequest("run-1", null)))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(experimentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithNameTakenInProjectThrowsConflict() {
        Project project = project("fraud");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.existsByProjectIdAndName(project.getId(), "run-1")).thenReturn(true);

        assertThatThrownBy(() -> experimentService.create(project.getId(), new CreateExperimentRequest("run-1", null)))
                .isInstanceOf(ExperimentNameAlreadyTakenException.class);
        verify(experimentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesUniqueViolationFromConcurrentInsert() {
        Project project = project("fraud");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.existsByProjectIdAndName(project.getId(), "run-1")).thenReturn(false);
        when(experimentRepository.saveAndFlush(any(Experiment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_experiments_project_name"));

        assertThatThrownBy(() -> experimentService.create(project.getId(), new CreateExperimentRequest("run-1", null)))
                .isInstanceOf(ExperimentNameAlreadyTakenException.class);
    }

    @Test
    void getByIdReturnsTagsSortedByName() {
        Experiment experiment = experiment(project("fraud"), "run-1");
        experiment.addTag(tag("xgboost"));
        experiment.addTag(tag("baseline"));
        when(experimentRepository.findById(experiment.getId())).thenReturn(Optional.of(experiment));

        ExperimentResponse response = experimentService.getById(experiment.getId());

        assertThat(response.tags()).extracting(TagResponse::name).containsExactly("baseline", "xgboost");
    }

    @Test
    void getUnknownExperimentThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.getById(id)).isInstanceOf(ExperimentNotFoundException.class);
    }

    @Test
    void findByProjectWithoutTagListsAllExperimentsOfProject() {
        Project project = project("fraud");
        Pageable pageable = PageRequest.of(0, 20);
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(experimentRepository.findByProjectId(project.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(experiment(project, "run-1")), pageable, 1));

        assertThat(experimentService.findByProject(project.getId(), null, pageable).getContent())
                .extracting(ExperimentResponse::name).containsExactly("run-1");
        verify(experimentRepository, never()).findByProjectIdAndTagsId(any(), any(), any());
    }

    @Test
    void findByProjectWithTagUsesTagFilter() {
        Project project = project("fraud");
        UUID tagId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(experimentRepository.findByProjectIdAndTagsId(project.getId(), tagId, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(experimentService.findByProject(project.getId(), tagId, pageable).getTotalElements()).isZero();
        verify(experimentRepository, never()).findByProjectId(any(), any());
    }

    @Test
    void findByUnknownProjectThrowsNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThatThrownBy(() -> experimentService.findByProject(projectId, null, PageRequest.of(0, 20)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void updateRenamesExperiment() {
        Experiment experiment = experiment(project("fraud"), "run-1");
        when(experimentRepository.findById(experiment.getId())).thenReturn(Optional.of(experiment));
        when(experimentRepository.existsByProjectIdAndName(experiment.getProject().getId(), "run-2")).thenReturn(false);

        ExperimentResponse response = experimentService.update(experiment.getId(), new UpdateExperimentRequest("run-2", "new"));

        assertThat(response.name()).isEqualTo("run-2");
        assertThat(response.description()).isEqualTo("new");
    }

    @Test
    void updateToNameTakenInProjectThrowsConflict() {
        Experiment experiment = experiment(project("fraud"), "run-1");
        when(experimentRepository.findById(experiment.getId())).thenReturn(Optional.of(experiment));
        when(experimentRepository.existsByProjectIdAndName(experiment.getProject().getId(), "run-2")).thenReturn(true);

        assertThatThrownBy(() -> experimentService.update(experiment.getId(), new UpdateExperimentRequest("run-2", null)))
                .isInstanceOf(ExperimentNameAlreadyTakenException.class);
        assertThat(experiment.getName()).isEqualTo("run-1");
    }

    @Test
    void deleteRemovesExistingExperiment() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.existsById(id)).thenReturn(true);

        experimentService.delete(id);

        verify(experimentRepository).deleteById(id);
    }

    @Test
    void deleteThrowsNotFoundForUnknownExperiment() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> experimentService.delete(id))
                .isInstanceOf(ExperimentNotFoundException.class);
        verify(experimentRepository, never()).deleteById(any());
    }

    @Test
    void assignTagAddsTagOnceEvenWhenRepeated() {
        Experiment experiment = experiment(project("fraud"), "run-1");
        Tag tag = tag("baseline");
        when(experimentRepository.findById(experiment.getId())).thenReturn(Optional.of(experiment));
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));

        experimentService.assignTag(experiment.getId(), tag.getId());
        experimentService.assignTag(experiment.getId(), tag.getId());

        assertThat(experiment.getTags()).containsExactly(tag);
    }

    @Test
    void assignUnknownTagThrowsNotFound() {
        Experiment experiment = experiment(project("fraud"), "run-1");
        UUID tagId = UUID.randomUUID();
        when(experimentRepository.findById(experiment.getId())).thenReturn(Optional.of(experiment));
        when(tagRepository.findById(tagId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.assignTag(experiment.getId(), tagId))
                .isInstanceOf(TagNotFoundException.class);
    }

    @Test
    void assignTagToUnknownExperimentThrowsNotFound() {
        UUID experimentId = UUID.randomUUID();
        when(experimentRepository.findById(experimentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.assignTag(experimentId, UUID.randomUUID()))
                .isInstanceOf(ExperimentNotFoundException.class);
    }

    @Test
    void removeTagDetachesTag() {
        Experiment experiment = experiment(project("fraud"), "run-1");
        Tag tag = tag("baseline");
        experiment.addTag(tag);
        when(experimentRepository.findById(experiment.getId())).thenReturn(Optional.of(experiment));
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));

        experimentService.removeTag(experiment.getId(), tag.getId());

        assertThat(experiment.getTags()).isEmpty();
    }
}
