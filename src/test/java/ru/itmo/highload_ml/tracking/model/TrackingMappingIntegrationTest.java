package ru.itmo.highload_ml.tracking.model;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TrackingMappingIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;

    @Test
    void persistsRunWithUuidBoundaryAndStringStatus() {
        UUID experimentId = createExperiment();
        UUID authorId = UUID.randomUUID();
        Run run = new Run(experimentId, authorId, "training");
        entityManager.persist(run);
        entityManager.flush();
        UUID runId = run.getId();
        entityManager.clear();

        Run restored = entityManager.find(Run.class, runId);
        assertThat(restored.getExperimentId()).isEqualTo(experimentId);
        assertThat(restored.getAuthorId()).isEqualTo(authorId);
        assertThat(restored.getName()).isEqualTo("training");
        assertThat(restored.getStatus()).isEqualTo(RunStatus.CREATED);
        assertThat(restored.getCreatedAt()).isNotNull();
        assertThat(restored.getStartedAt()).isNull();
        assertThat(restored.getFinishedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, runId))
                .isEqualTo("CREATED");
    }

    @Test
    void persistsMetricAndArtifactAsChildrenOfRun() {
        Run run = new Run(createExperiment(), UUID.randomUUID(), "training");
        Instant startedAt = Instant.now();
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(startedAt);
        entityManager.persist(run);

        Metric metric = new Metric(run, "accuracy", new BigDecimal("0.95000000"), 1L);
        Artifact artifact = new Artifact(run, "weights", ArtifactType.MODEL, "models/weights.bin", 1024L);
        entityManager.persist(metric);
        entityManager.persist(artifact);
        entityManager.flush();
        UUID runId = run.getId();
        UUID metricId = metric.getId();
        UUID artifactId = artifact.getId();
        entityManager.clear();

        Run restored = entityManager.find(Run.class, runId);
        assertThat(restored.getMetrics()).hasSize(1);
        assertThat(restored.getArtifacts()).hasSize(1);
        Metric restoredMetric = entityManager.find(Metric.class, metricId);
        assertThat(restoredMetric.getRun().getId()).isEqualTo(runId);
        assertThat(restoredMetric.getName()).isEqualTo("accuracy");
        assertThat(restoredMetric.getValue()).isEqualByComparingTo("0.95");
        assertThat(restoredMetric.getStep()).isEqualTo(1L);
        assertThat(restoredMetric.getRecordedAt()).isNotNull();
        Artifact restoredArtifact = entityManager.find(Artifact.class, artifactId);
        assertThat(restoredArtifact.getRun().getId()).isEqualTo(runId);
        assertThat(restoredArtifact.getType()).isEqualTo(ArtifactType.MODEL);
        assertThat(restoredArtifact.getPath()).isEqualTo("models/weights.bin");
        assertThat(restoredArtifact.getSizeBytes()).isEqualTo(1024L);
        assertThat(restoredArtifact.getCreatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT type FROM artifacts WHERE id = ?", String.class, artifactId))
                .isEqualTo("MODEL");
    }

    private UUID createExperiment() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        return experimentRepository.saveAndFlush(new Experiment(project, "baseline", null)).getId();
    }
}
