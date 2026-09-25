package ru.itmo.highload_ml.registry.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.itmo.highload_ml.project.exception.NotProjectMemberException;
import ru.itmo.highload_ml.registry.RegistryModuleIntegrationTest;
import ru.itmo.highload_ml.registry.api.dto.RegisterModelVersionRequest;
import ru.itmo.highload_ml.registry.exception.InvalidModelArtifactException;
import ru.itmo.highload_ml.registry.exception.InvalidModelVersionTransitionException;
import ru.itmo.highload_ml.registry.exception.ModelArtifactAlreadyRegisteredException;
import ru.itmo.highload_ml.registry.exception.ModelVersionNotFoundException;
import ru.itmo.highload_ml.registry.model.ModelVersionState;
import ru.itmo.highload_ml.registry.repository.ModelVersionRepository;
import ru.itmo.highload_ml.tracking.model.ArtifactType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelVersionServiceIntegrationTest extends RegistryModuleIntegrationTest {

    @Autowired private ModelVersionService service;
    @Autowired private ModelVersionRepository repository;

    @Test
    void registersProjectLocalVersionsAndRejectsInvalidSources() {
        Fixture fixture = createFixture();
        UUID firstArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        UUID secondArtifact = createArtifact(fixture, ArtifactType.MODEL, true);

        var first = register(fixture, firstArtifact);
        var second = register(fixture, secondArtifact);

        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(first.state()).isEqualTo(ModelVersionState.NEW);
        assertThat(first.promotedAt()).isNull();
        assertThat(service.getById(fixture.projectId(), first.id())).isEqualTo(first);
        assertThat(service.findAll(fixture.projectId(), PageRequest.of(0, 1,
                Sort.by(Sort.Direction.DESC, "version"))).getContent()).containsExactly(second);
        assertThat(service.findAll(fixture.projectId(), PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
        assertThatThrownBy(() -> service.getProduction(fixture.projectId()))
                .isInstanceOf(ModelVersionNotFoundException.class);
        assertThatThrownBy(() -> register(fixture, firstArtifact))
                .isInstanceOf(ModelArtifactAlreadyRegisteredException.class);
        assertThat(repository.count()).isEqualTo(2);

        Fixture other = createFixture();
        assertThat(register(other, createArtifact(other, ArtifactType.MODEL, true)).version()).isEqualTo(1);
        assertThatThrownBy(() -> service.getById(other.projectId(), first.id()))
                .isInstanceOf(ModelVersionNotFoundException.class);
        assertThatThrownBy(() -> service.register(fixture.projectId(),
                new RegisterModelVersionRequest(UUID.randomUUID(), secondArtifact)))
                .isInstanceOf(NotProjectMemberException.class);
        assertThatThrownBy(() -> register(fixture, createArtifact(fixture, ArtifactType.LOG, true)))
                .isInstanceOf(InvalidModelArtifactException.class);
        assertThatThrownBy(() -> register(fixture, createArtifact(fixture, ArtifactType.MODEL, false)))
                .isInstanceOf(InvalidModelArtifactException.class);
        assertThatThrownBy(() -> register(fixture, createArtifact(other, ArtifactType.MODEL, true)))
                .isInstanceOf(InvalidModelArtifactException.class);
        assertThat(register(fixture, createArtifact(fixture, ArtifactType.MODEL, true)).version())
                .isEqualTo(3);
    }

    @Test
    void promotionArchivesPreviousProductionAndRejectsInvalidTransitions() {
        Fixture fixture = createFixture();
        var first = register(fixture, createArtifact(fixture, ArtifactType.MODEL, true));
        var second = register(fixture, createArtifact(fixture, ArtifactType.MODEL, true));

        assertThatThrownBy(() -> service.promoteToProduction(fixture.projectId(), first.id(), fixture.userId()))
                .isInstanceOf(InvalidModelVersionTransitionException.class);
        assertThat(service.stage(fixture.projectId(), first.id(), fixture.userId()).state())
                .isEqualTo(ModelVersionState.STAGING);
        assertThatThrownBy(() -> service.stage(fixture.projectId(), first.id(), fixture.userId()))
                .isInstanceOf(InvalidModelVersionTransitionException.class);
        var promotedFirst = service.promoteToProduction(fixture.projectId(), first.id(), fixture.userId());
        assertThat(promotedFirst.promotedAt()).isNotNull();
        assertThat(service.getProduction(fixture.projectId())).isEqualTo(promotedFirst);

        service.stage(fixture.projectId(), second.id(), fixture.userId());
        var promotedSecond = service.promoteToProduction(fixture.projectId(), second.id(), fixture.userId());
        assertThat(promotedSecond.state()).isEqualTo(ModelVersionState.PRODUCTION);
        assertThat(service.getProduction(fixture.projectId())).isEqualTo(promotedSecond);
        assertThat(service.getById(fixture.projectId(), first.id()).state()).isEqualTo(ModelVersionState.ARCHIVED);
        assertThat(service.getById(fixture.projectId(), first.id()).promotedAt())
                .isEqualTo(promotedFirst.promotedAt());
        assertThatThrownBy(() -> service.promoteToProduction(fixture.projectId(), first.id(), fixture.userId()))
                .isInstanceOf(InvalidModelVersionTransitionException.class);
        assertThatThrownBy(() -> service.promoteToProduction(fixture.projectId(), UUID.randomUUID(), fixture.userId()))
                .isInstanceOf(ModelVersionNotFoundException.class);
    }

    private ru.itmo.highload_ml.registry.api.dto.ModelVersionResponse register(Fixture fixture, UUID artifactId) {
        return service.register(fixture.projectId(), new RegisterModelVersionRequest(fixture.userId(), artifactId));
    }
}
