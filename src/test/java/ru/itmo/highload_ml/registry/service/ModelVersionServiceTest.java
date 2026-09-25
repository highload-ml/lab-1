package ru.itmo.highload_ml.registry.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.project.exception.NotProjectMemberException;
import ru.itmo.highload_ml.project.port.ExperimentAccessPort;
import ru.itmo.highload_ml.project.port.ProjectAccessPort;
import ru.itmo.highload_ml.registry.api.dto.RegisterModelVersionRequest;
import ru.itmo.highload_ml.registry.exception.InvalidModelArtifactException;
import ru.itmo.highload_ml.registry.exception.InvalidModelVersionTransitionException;
import ru.itmo.highload_ml.registry.exception.ModelVersionNotFoundException;
import ru.itmo.highload_ml.registry.mapper.ModelVersionMapper;
import ru.itmo.highload_ml.registry.model.ModelVersion;
import ru.itmo.highload_ml.registry.model.ModelVersionState;
import ru.itmo.highload_ml.registry.repository.ModelVersionRepository;
import ru.itmo.highload_ml.registry.model.RegistryVersionCounter;
import ru.itmo.highload_ml.registry.repository.RegistryVersionCounterRepository;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.port.ArtifactDetails;
import ru.itmo.highload_ml.tracking.port.ArtifactLookupPort;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelVersionServiceTest {

    @Mock private ProjectAccessPort projectAccessPort;
    @Mock private ExperimentAccessPort experimentAccessPort;
    @Mock private ArtifactLookupPort artifactLookupPort;
    @Mock private ModelVersionRepository repository;
    @Mock private RegistryVersionCounterRepository counterRepository;
    @Spy private ModelVersionMapper mapper = new ModelVersionMapper();
    @InjectMocks private ModelVersionService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID experimentId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();

    @Test
    void registerAllocatesOnlyAfterValidatingArtifact() {
        when(artifactLookupPort.getDetails(artifactId)).thenReturn(
                new ArtifactDetails(artifactId, experimentId, ArtifactType.MODEL, RunStatus.COMPLETED));
        when(experimentAccessPort.getProjectId(experimentId)).thenReturn(projectId);
        when(counterRepository.findById(projectId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ModelVersion.class))).thenAnswer(invocation -> {
            ModelVersion version = invocation.getArgument(0);
            ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(version, "createdAt", Instant.now());
            return version;
        });

        var response = service.register(projectId, request());

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.state()).isEqualTo(ModelVersionState.NEW);
        var calls = inOrder(projectAccessPort, artifactLookupPort, experimentAccessPort, counterRepository, repository);
        calls.verify(projectAccessPort).requireMember(projectId, userId);
        calls.verify(artifactLookupPort).getDetails(artifactId);
        calls.verify(experimentAccessPort).getProjectId(experimentId);
        calls.verify(counterRepository).save(any(RegistryVersionCounter.class));
        calls.verify(repository).saveAndFlush(any(ModelVersion.class));
    }

    @Test
    void registerContinuesExistingProjectCounter() {
        when(artifactLookupPort.getDetails(artifactId)).thenReturn(
                new ArtifactDetails(artifactId, experimentId, ArtifactType.MODEL, RunStatus.COMPLETED));
        when(experimentAccessPort.getProjectId(experimentId)).thenReturn(projectId);
        RegistryVersionCounter existing = new RegistryVersionCounter(projectId);
        existing.allocate();
        existing.allocate();
        when(counterRepository.findById(projectId)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(ModelVersion.class))).thenAnswer(invocation -> {
            ModelVersion version = invocation.getArgument(0);
            ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(version, "createdAt", Instant.now());
            return version;
        });

        assertThat(service.register(projectId, request()).version()).isEqualTo(3);
        assertThat(existing.getNextVersion()).isEqualTo(4);
        verify(counterRepository).save(existing);
    }

    @Test
    void registerRejectsNonMemberBeforeLookingUpArtifact() {
        doThrow(new NotProjectMemberException(projectId, userId))
                .when(projectAccessPort).requireMember(projectId, userId);

        assertThatThrownBy(() -> service.register(projectId, request()))
                .isInstanceOf(NotProjectMemberException.class);
        verify(artifactLookupPort, never()).getDetails(any());
    }

    @Test
    void registerRejectsWrongArtifactTypeBeforeAllocatingNumber() {
        when(artifactLookupPort.getDetails(artifactId)).thenReturn(
                new ArtifactDetails(artifactId, experimentId, ArtifactType.LOG, RunStatus.COMPLETED));

        assertThatThrownBy(() -> service.register(projectId, request()))
                .isInstanceOf(InvalidModelArtifactException.class);
        verify(counterRepository, never()).save(any());
    }

    @Test
    void stageRequiresNewVersion() {
        ModelVersion version = version();
        when(repository.findByIdAndProjectId(version.getId(), projectId))
                .thenReturn(Optional.of(version));

        assertThat(service.stage(projectId, version.getId(), userId).state())
                .isEqualTo(ModelVersionState.STAGING);
        assertThatThrownBy(() -> service.stage(projectId, version.getId(), userId))
                .isInstanceOf(InvalidModelVersionTransitionException.class);
        verify(repository).flush();
    }

    @Test
    void promotionArchivesOldProductionBeforePromotingTarget() {
        ModelVersion old = version();
        old.stage();
        old.promote(Instant.now().minusSeconds(10));
        ModelVersion target = version();
        target.stage();
        when(repository.findByIdAndProjectId(target.getId(), projectId))
                .thenReturn(Optional.of(target));
        when(repository.findByProjectIdAndState(projectId, ModelVersionState.PRODUCTION))
                .thenReturn(Optional.of(old));

        var promoted = service.promoteToProduction(projectId, target.getId(), userId);

        assertThat(old.getState()).isEqualTo(ModelVersionState.ARCHIVED);
        assertThat(promoted.state()).isEqualTo(ModelVersionState.PRODUCTION);
        assertThat(promoted.promotedAt()).isNotNull();
        var calls = inOrder(repository);
        calls.verify(repository).findByIdAndProjectId(target.getId(), projectId);
        calls.verify(repository).findByProjectIdAndState(projectId, ModelVersionState.PRODUCTION);
        calls.verify(repository, times(2)).flush();
    }

<<<<<<< HEAD
=======
    @Test
    void promotionRejectsMissingVersion() {
        UUID missingId = UUID.randomUUID();
        assertThatThrownBy(() -> service.promoteToProduction(projectId, missingId, userId))
                .isInstanceOf(ModelVersionNotFoundException.class);
        verify(repository).findByIdAndProjectId(missingId, projectId);
    }

>>>>>>> 777bf4594fd0306c9029215022fb36840cd757b1
    private RegisterModelVersionRequest request() {
        return new RegisterModelVersionRequest(userId, artifactId);
    }

    private ModelVersion version() {
        ModelVersion version = new ModelVersion(projectId, UUID.randomUUID(), 1);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(version, "createdAt", Instant.now());
        return version;
    }
}
