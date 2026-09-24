package ru.itmo.highload_ml.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.itmo.highload_ml.registry.api.dto.RegisterModelVersionRequest;
import ru.itmo.highload_ml.registry.service.ModelVersionService;
import ru.itmo.highload_ml.tracking.model.ArtifactType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrySchemaIntegrationTest extends RegistryModuleIntegrationTest {

    @Autowired private ModelVersionService service;

    @Test
    void installsTablesConstraintsAndPartialProductionIndex() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                AND table_name IN ('model_versions', 'registry_version_counters')
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder("model_versions", "registry_version_counters");

        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint WHERE conname IN (
                    'fk_model_versions_project', 'fk_model_versions_artifact',
                    'uk_model_versions_project_version', 'uk_model_versions_artifact',
                    'ck_model_versions_version', 'ck_model_versions_state',
                    'ck_model_versions_promoted_at', 'fk_registry_version_counters_project')
                """, String.class);
        assertThat(constraints).hasSize(8);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_model_versions_one_production'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseEnforcesArtifactUniquenessAndSingleProduction() {
        Fixture fixture = createFixture();
        UUID firstArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        UUID secondArtifact = createArtifact(fixture, ArtifactType.MODEL, true);
        var first = service.register(fixture.projectId(),
                new RegisterModelVersionRequest(fixture.userId(), firstArtifact));
        var second = service.register(fixture.projectId(),
                new RegisterModelVersionRequest(fixture.userId(), secondArtifact));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO model_versions (id, project_id, artifact_id, version, state, created_at)
                VALUES (?, ?, ?, ?, 'NEW', ?)
                """, UUID.randomUUID(), fixture.projectId(), firstArtifact, 3L, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE model_versions SET state = 'PRODUCTION' WHERE id = ?
                """, first.id())).isInstanceOf(DataIntegrityViolationException.class);

        service.stage(fixture.projectId(), first.id(), fixture.userId());
        service.stage(fixture.projectId(), second.id(), fixture.userId());
        service.promoteToProduction(fixture.projectId(), first.id(), fixture.userId());
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE model_versions SET state = 'PRODUCTION', promoted_at = ? WHERE id = ?
                """, Timestamp.from(Instant.now()), second.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
