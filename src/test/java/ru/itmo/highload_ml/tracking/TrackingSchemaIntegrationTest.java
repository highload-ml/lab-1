package ru.itmo.highload_ml.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.BaseIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class TrackingSchemaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void installsTablesAndNamedConstraints() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('runs', 'metrics', 'artifacts')
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder("runs", "metrics", "artifacts");

        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint WHERE conname IN (
                    'fk_runs_experiment', 'ck_runs_status', 'ck_runs_status_timestamps',
                    'fk_metrics_run', 'uk_metrics_run_name_step', 'ck_metrics_step_nonnegative',
                    'fk_artifacts_run', 'uk_artifacts_run_name', 'ck_artifacts_type',
                    'ck_artifacts_size_nonnegative')
                """, String.class);
        assertThat(constraints).containsExactlyInAnyOrder(
                "fk_runs_experiment", "ck_runs_status", "ck_runs_status_timestamps",
                "fk_metrics_run", "uk_metrics_run_name_step", "ck_metrics_step_nonnegative",
                "fk_artifacts_run", "uk_artifacts_run_name", "ck_artifacts_type",
                "ck_artifacts_size_nonnegative");
    }

    @Test
    void acceptsValidRunMetricAndArtifact() {
        UUID runId = insertRun();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                INSERT INTO metrics (id, run_id, name, value, step, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), runId, "accuracy", 0.95, 1L, Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO artifacts (id, run_id, name, type, path, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), runId, "weights", "MODEL", "models/weights.bin", 1024L,
                Timestamp.from(now));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM metrics WHERE run_id = ?", Long.class, runId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifacts WHERE run_id = ?", Long.class, runId))
                .isEqualTo(1);
    }

    @Test
    void rejectsRunWithoutExperiment() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO runs (id, experiment_id, author_id, name, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "orphan", "CREATED",
                Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateMetricStepWithinRun() {
        UUID runId = insertRun();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO metrics (id, run_id, name, value, step, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), runId, "accuracy", 0.95, 1L, Timestamp.from(now));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO metrics (id, run_id, name, value, step, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), runId, "accuracy", 0.96, 1L, Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertRun() {
        UUID projectId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update("INSERT INTO projects (id, name, created_at) VALUES (?, ?, ?)",
                projectId, "project-" + projectId, Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO experiments (id, project_id, name, created_at)
                VALUES (?, ?, ?, ?)
                """, experimentId, projectId, "baseline", Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO runs (id, experiment_id, author_id, name, status, created_at, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, runId, experimentId, UUID.randomUUID(), "training", "RUNNING",
                Timestamp.from(now), Timestamp.from(now));
        return runId;
    }
}
