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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Test
    void concurrentRegistrationsGetDistinctVersions() {
        Fixture fixture = createFixture();
        UUID firstArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        UUID secondArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> {
                await(barrier);
                return register(fixture, firstArtifact).version();
            }, executor);
            var second = CompletableFuture.supplyAsync(() -> {
                await(barrier);
                return register(fixture, secondArtifact).version();
            }, executor);
            assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(1L, 2L);
        }
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void concurrentPromotionsLeaveExactlyOneProduction() {
        Fixture fixture = createFixture();
        var first = register(fixture, createArtifact(fixture, ArtifactType.MODEL, true));
        var second = register(fixture, createArtifact(fixture, ArtifactType.MODEL, true));
        service.stage(fixture.projectId(), first.id(), fixture.userId());
        service.stage(fixture.projectId(), second.id(), fixture.userId());
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = CompletableFuture.runAsync(() -> {
                await(barrier);
                service.promoteToProduction(fixture.projectId(), first.id(), fixture.userId());
            }, executor);
            var b = CompletableFuture.runAsync(() -> {
                await(barrier);
                service.promoteToProduction(fixture.projectId(), second.id(), fixture.userId());
            }, executor);
            CompletableFuture.allOf(a, b).join();
        }

        assertThat(repository.findAll()).extracting(version -> version.getState())
                .containsExactlyInAnyOrder(ModelVersionState.PRODUCTION, ModelVersionState.ARCHIVED);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM model_versions WHERE project_id = ? AND state = 'PRODUCTION'
                """, Long.class, fixture.projectId())).isEqualTo(1);
    }

    private ru.itmo.highload_ml.registry.api.dto.ModelVersionResponse register(Fixture fixture, UUID artifactId) {
        return service.register(fixture.projectId(), new RegisterModelVersionRequest(fixture.userId(), artifactId));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
