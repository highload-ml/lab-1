package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.project.api.dto.CreateExperimentRequest;
import ru.itmo.highload_ml.project.exception.ExperimentNameAlreadyTakenException;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.exception.ProjectNotFoundException;
import ru.itmo.highload_ml.project.exception.TagNotFoundException;
import ru.itmo.highload_ml.project.mapper.ExperimentMapper;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.TagRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.itmo.highload_ml.project.TestEntities.project;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private TagRepository tagRepository;

    @Spy
    private ExperimentMapper mapper = new ExperimentMapper();

    @InjectMocks
    private ExperimentService service;

    @Test
    void createSavesExperimentInLockedProject() {
        Project project = project("fraud");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.saveAndFlush(any(Experiment.class))).thenAnswer(invocation -> {
            Experiment experiment = invocation.getArgument(0);
            ReflectionTestUtils.setField(experiment, "id", UUID.randomUUID());
            return experiment;
        });

        var response = service.create(project.getId(), new CreateExperimentRequest("baseline", "description"));

        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.name()).isEqualTo("baseline");
        assertThat(response.tags()).isEmpty();
    }

    @Test
    void createRejectsUnknownProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(projectId, new CreateExperimentRequest("baseline", null)))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(experimentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsTakenName() {
        Project project = project("fraud");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.existsByProject_IdAndName(project.getId(), "baseline")).thenReturn(true);

        assertThatThrownBy(() -> service.create(project.getId(), new CreateExperimentRequest("baseline", null)))
                .isInstanceOf(ExperimentNameAlreadyTakenException.class);
        verify(experimentRepository, never()).saveAndFlush(any());
    }

    @Test
    void getByIdDoesNotReturnExperimentFromAnotherProject() {
        Project project = project("fraud");
        UUID experimentId = UUID.randomUUID();
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(experimentRepository.findWithTagsByIdAndProject_Id(experimentId, project.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(project.getId(), experimentId))
                .isInstanceOf(ExperimentNotFoundException.class);
    }

    @Test
    void updateChangesNameAndDescriptionWithinLockedProject() {
        Project project = project("fraud");
        Experiment experiment = new Experiment(project, "old", null);
        UUID experimentId = UUID.randomUUID();
        ReflectionTestUtils.setField(experiment, "id", experimentId);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.findByIdAndProject_Id(experimentId, project.getId()))
                .thenReturn(Optional.of(experiment));

        var response = service.update(project.getId(), experimentId, new CreateExperimentRequest("new", "updated"));

        assertThat(response.name()).isEqualTo("new");
        assertThat(response.description()).isEqualTo("updated");
        verify(experimentRepository).flush();
    }

    @Test
    void addTagRejectsUnknownTag() {
        Project project = project("fraud");
        Experiment experiment = new Experiment(project, "baseline", null);
        UUID experimentId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        ReflectionTestUtils.setField(experiment, "id", experimentId);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(experimentRepository.findByIdAndProject_Id(experimentId, project.getId()))
                .thenReturn(Optional.of(experiment));
        when(tagRepository.findById(tagId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addTag(project.getId(), experimentId, tagId))
                .isInstanceOf(TagNotFoundException.class);
        verify(experimentRepository, never()).flush();
    }
}
