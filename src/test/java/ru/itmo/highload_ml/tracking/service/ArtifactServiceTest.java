package ru.itmo.highload_ml.tracking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.tracking.api.dto.CreateArtifactRequest;
import ru.itmo.highload_ml.tracking.exception.ArtifactNameAlreadyTakenException;
import ru.itmo.highload_ml.tracking.exception.ArtifactNotFoundException;
import ru.itmo.highload_ml.tracking.exception.RunNotAcceptingArtifactsException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.mapper.ArtifactMapper;
import ru.itmo.highload_ml.tracking.model.Artifact;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.ArtifactRepository;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock private RunRepository runRepository;
    @Mock private ArtifactRepository artifactRepository;
    @Spy private ArtifactMapper mapper = new ArtifactMapper();
    @InjectMocks private ArtifactService service;

    @Test
    void registersMetadataForRunningRun() {
        Run run = run(RunStatus.RUNNING);
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(artifactRepository.saveAndFlush(any(Artifact.class))).thenAnswer(invocation -> {
            Artifact artifact = invocation.getArgument(0);
            ReflectionTestUtils.setField(artifact, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(artifact, "createdAt", Instant.now());
            return artifact;
        });

        var response = service.register(run.getId(), request());

        assertThat(response.runId()).isEqualTo(run.getId());
        assertThat(response.name()).isEqualTo("weights");
        assertThat(response.type()).isEqualTo(ArtifactType.MODEL);
        assertThat(response.path()).isEqualTo("models/weights.bin");
        assertThat(response.sizeBytes()).isEqualTo(1024L);
        assertThat(response.createdAt()).isNotNull();
        verify(artifactRepository).saveAndFlush(any(Artifact.class));
    }

    @Test
    void rejectsRegistrationBeforeStartOrAfterCompletion() {
        for (RunStatus status : List.of(RunStatus.CREATED, RunStatus.COMPLETED, RunStatus.FAILED)) {
            Run run = run(status);
            when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
            assertThatThrownBy(() -> service.register(run.getId(), request()))
                    .isInstanceOf(RunNotAcceptingArtifactsException.class);
        }
        verify(artifactRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExistingNameBeforeInsert() {
        Run run = run(RunStatus.RUNNING);
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(artifactRepository.existsByRun_IdAndName(run.getId(), "weights")).thenReturn(true);

        assertThatThrownBy(() -> service.register(run.getId(), request()))
                .isInstanceOf(ArtifactNameAlreadyTakenException.class);
        verify(artifactRepository, never()).saveAndFlush(any());
    }

    @Test
    void readsArtifactOnlyThroughItsOwnRun() {
        Run run = run(RunStatus.RUNNING);
        UUID artifactId = UUID.randomUUID();
        when(runRepository.existsById(run.getId())).thenReturn(true);
        when(artifactRepository.findByIdAndRun_Id(artifactId, run.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(run.getId(), artifactId))
                .isInstanceOf(ArtifactNotFoundException.class);
    }

    @Test
    void missingRunIsNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(runId, request()))
                .isInstanceOf(RunNotFoundException.class);
    }

    private static CreateArtifactRequest request() {
        return new CreateArtifactRequest("weights", ArtifactType.MODEL, "models/weights.bin", 1024L);
    }

    private static Run run(RunStatus status) {
        Run run = new Run(UUID.randomUUID(), UUID.randomUUID(), "training");
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setStatus(status);
        return run;
    }
}
