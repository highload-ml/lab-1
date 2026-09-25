package ru.itmo.highload_ml.tracking.service;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.exception.NotProjectMemberException;
import ru.itmo.highload_ml.project.port.ExperimentAccessPort;
import ru.itmo.highload_ml.project.port.ProjectAccessPort;
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.exception.InvalidRunStateTransitionException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.mapper.RunMapper;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunServiceTest {

    @Mock private ExperimentAccessPort experimentAccessPort;
    @Mock private ProjectAccessPort projectAccessPort;
    @Mock private RunRepository runRepository;
    @Spy private RunMapper mapper = new RunMapper();
    @InjectMocks private RunService service;

    @Test
    void createChecksMembershipBeforeSaving() {
        UUID experimentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(experimentAccessPort.getProjectId(experimentId)).thenReturn(projectId);
        when(runRepository.saveAndFlush(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(run, "createdAt", Instant.now());
            return run;
        });

        var response = service.create(experimentId, new CreateRunRequest(authorId, "training"));

        assertThat(response.experimentId()).isEqualTo(experimentId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.status()).isEqualTo(RunStatus.CREATED);
        assertThat(response.createdAt()).isNotNull();
        var calls = inOrder(experimentAccessPort, projectAccessPort, runRepository);
        calls.verify(experimentAccessPort).getProjectId(experimentId);
        calls.verify(projectAccessPort).requireMember(projectId, authorId);
        calls.verify(runRepository).saveAndFlush(any(Run.class));
    }

    @Test
    void createRejectsNonMemberWithoutSaving() {
        UUID experimentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(experimentAccessPort.getProjectId(experimentId)).thenReturn(projectId);
        org.mockito.Mockito.doThrow(new NotProjectMemberException(projectId, authorId))
                .when(projectAccessPort).requireMember(projectId, authorId);

        assertThatThrownBy(() -> service.create(experimentId, new CreateRunRequest(authorId, "training")))
                .isInstanceOf(NotProjectMemberException.class);
        verify(runRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesConcurrentExperimentDeletion() {
        UUID experimentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(experimentAccessPort.getProjectId(experimentId)).thenReturn(projectId);
        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("fk_runs_experiment");
        when(runRepository.saveAndFlush(any(Run.class)))
                .thenThrow(new DataIntegrityViolationException("foreign key", violation));

        assertThatThrownBy(() -> service.create(experimentId,
                new CreateRunRequest(UUID.randomUUID(), "training")))
                .isInstanceOf(ExperimentNotFoundException.class);
    }

    @Test
    void startChangesCreatedRunAndRejectsRepeatedStart() {
        Run run = run();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        var response = service.start(run.getId());

        assertThat(response.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.finishedAt()).isNull();
        verify(runRepository).flush();
        assertThatThrownBy(() -> service.start(run.getId()))
                .isInstanceOf(InvalidRunStateTransitionException.class);
    }

    @Test
    void completeRequiresRunningAndSetsFinishTime() {
        Run run = run();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.complete(run.getId()))
                .isInstanceOf(InvalidRunStateTransitionException.class);
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now().minusSeconds(1));

        var response = service.complete(run.getId());

        assertThat(response.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(response.finishedAt()).isAfterOrEqualTo(response.startedAt());
        verify(runRepository).flush();
    }

    @Test
    void missingRunReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(runRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(RunNotFoundException.class);
    }

    private static Run run() {
        Run run = new Run(UUID.randomUUID(), UUID.randomUUID(), "training");
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(run, "createdAt", Instant.now());
        return run;
    }
}
