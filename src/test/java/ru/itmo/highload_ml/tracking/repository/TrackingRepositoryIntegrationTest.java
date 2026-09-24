package ru.itmo.highload_ml.tracking.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.tracking.model.Artifact;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.Metric;
import ru.itmo.highload_ml.tracking.model.Run;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingRepositoryIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private RunRepository runRepository;
    @Autowired private MetricRepository metricRepository;
    @Autowired private ArtifactRepository artifactRepository;

    @Test
    void keysetSliceIncludesTiedTimestampsWithoutDuplicates() {
        UUID experimentId = createExperiment();
        UUID otherExperimentId = createExperiment();
        List<Run> runs = List.of(createRun(experimentId), createRun(experimentId), createRun(experimentId));
        createRun(otherExperimentId);
        Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");
        runs.forEach(run -> jdbcTemplate.update("UPDATE runs SET created_at = ? WHERE id = ?",
                Timestamp.from(sameTime), run.getId()));

        var first = runRepository.findByExperimentIdOrderByCreatedAtDescIdDesc(
                experimentId, PageRequest.of(0, 2));
        assertThat(first.hasNext()).isTrue();
        assertThat(first.getContent()).hasSize(2);
        Run cursor = first.getContent().getLast();
        var second = runRepository.findBeforeCursor(
                experimentId, cursor.getCreatedAt(), cursor.getId(), PageRequest.of(0, 2));

        assertThat(second.hasNext()).isFalse();
        assertThat(second.getContent()).hasSize(1);
        assertThat(new HashSet<>(first.getContent().stream().map(Run::getId).toList()))
                .doesNotContain(second.getContent().getFirst().getId());
        assertThat(first.getContent().stream().map(Run::getId).toList())
                .containsExactlyInAnyOrderElementsOf(runs.stream().map(Run::getId)
                        .filter(id -> !id.equals(second.getContent().getFirst().getId())).toList());
        assertThat(runRepository.existsByExperimentId(experimentId)).isTrue();
        assertThat(runRepository.existsByExperimentId(UUID.randomUUID())).isFalse();
    }

    @Test
    void metricAndArtifactQueriesAreScopedToRunAndDatabaseRejectsDuplicates() {
        Run run = createRun(createExperiment());
        Run otherRun = createRun(createExperiment());
        Metric metric = metricRepository.saveAndFlush(new Metric(run, "accuracy", new BigDecimal("0.95"), 1));
        Artifact artifact = artifactRepository.saveAndFlush(
                new Artifact(run, "weights", ArtifactType.MODEL, "models/weights.bin", 1024));

        assertThat(metricRepository.existsByRun_IdAndNameAndStep(run.getId(), "accuracy", 1)).isTrue();
        assertThat(metricRepository.existsByRun_IdAndNameAndStep(otherRun.getId(), "accuracy", 1)).isFalse();
        assertThat(metricRepository.findByRun_Id(run.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Metric::getId).containsExactly(metric.getId());
        assertThat(artifactRepository.existsByRun_IdAndName(run.getId(), "weights")).isTrue();
        assertThat(artifactRepository.findByIdAndRun_Id(artifact.getId(), otherRun.getId())).isEmpty();
        assertThat(artifactRepository.findByRun_Id(run.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Artifact::getId).containsExactly(artifact.getId());

        assertThatThrownBy(() -> metricRepository.saveAndFlush(
                new Metric(run, "accuracy", new BigDecimal("0.96"), 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> artifactRepository.saveAndFlush(
                new Artifact(run, "weights", ArtifactType.MODEL, "models/other.bin", 1024)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID createExperiment() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        return experimentRepository.saveAndFlush(new Experiment(project, "baseline", null)).getId();
    }

    private Run createRun(UUID experimentId) {
        return runRepository.saveAndFlush(new Run(experimentId, UUID.randomUUID(), "training"));
    }
}
