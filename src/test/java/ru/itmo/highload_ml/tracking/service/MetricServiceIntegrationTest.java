package ru.itmo.highload_ml.tracking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricRequest;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricsRequest;
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.exception.DuplicateMetricException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.exception.RunNotRunningException;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.MetricRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricServiceIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private MetricService metricService;
    @Autowired private RunService runService;
    @Autowired private MetricRepository metricRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;

    @Test
    void logsSingleAndBatchMetricsAndPaginatesResults() {
        UUID runId = createRunningRun();
        var first = metricService.log(runId, metric("accuracy", 1));
        var batch = metricService.logBatch(runId,
                new CreateMetricsRequest(List.of(metric("loss", 1), metric("accuracy", 2))));

        assertThat(first.id()).isNotNull();
        assertThat(first.recordedAt()).isNotNull();
        assertThat(batch).hasSize(2).allSatisfy(response -> {
            assertThat(response.id()).isNotNull();
            assertThat(response.runId()).isEqualTo(runId);
        });
        assertThat(metricRepository.count()).isEqualTo(3);
        var page = metricService.findAll(runId, PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThatThrownBy(() -> metricService.findAll(UUID.randomUUID(), PageRequest.of(0, 10)))
                .isInstanceOf(RunNotFoundException.class);
    }

    @Test
    void duplicateWithinBatchAndExistingMetricLeaveBatchUnwritten() {
        UUID runId = createRunningRun();
        assertThatThrownBy(() -> metricService.logBatch(runId,
                new CreateMetricsRequest(List.of(metric("accuracy", 1), metric("accuracy", 1)))))
                .isInstanceOf(DuplicateMetricException.class);
        assertThat(metricRepository.count()).isZero();

        metricService.log(runId, metric("loss", 1));
        assertThatThrownBy(() -> metricService.logBatch(runId,
                new CreateMetricsRequest(List.of(metric("accuracy", 1), metric("loss", 1)))))
                .isInstanceOf(DuplicateMetricException.class);
        assertThat(metricRepository.count()).isEqualTo(1);
    }

    @Test
    void databaseFailureRollsBackWholeBatch() {
        UUID runId = createRunningRun();
        CreateMetricRequest tooLarge = new CreateMetricRequest("overflow",
                new BigDecimal("100000000000.00000000"), 2L);

        assertThatThrownBy(() -> metricService.logBatch(runId,
                new CreateMetricsRequest(List.of(metric("accuracy", 1), tooLarge))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(metricRepository.count()).isZero();
    }

    @Test
    void completedRunRejectsMetrics() {
        UUID runId = createRunningRun();
        runService.complete(runId);

        assertThatThrownBy(() -> metricService.log(runId, metric("accuracy", 1)))
                .isInstanceOf(RunNotRunningException.class);
        assertThat(metricRepository.count()).isZero();
    }

    @Test
    void logAndCompleteSerializeOnRunRow() {
        UUID runId = createRunningRun();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var log = CompletableFuture.supplyAsync(() -> {
                await(barrier);
                try {
                    metricService.log(runId, metric("accuracy", 1));
                    return true;
                } catch (RunNotRunningException e) {
                    return false;
                }
            }, executor);
            var complete = CompletableFuture.runAsync(() -> {
                await(barrier);
                runService.complete(runId);
            }, executor);
            complete.join();
            boolean logged = log.join();

            assertThat(runService.getById(runId).status()).isEqualTo(RunStatus.COMPLETED);
            assertThat(metricRepository.count()).isEqualTo(logged ? 1 : 0);
            if (logged) {
                assertThat(metricRepository.findAll().getFirst().getRecordedAt())
                        .isBeforeOrEqualTo(runService.getById(runId).finishedAt());
            }
        }
    }

    private UUID createRunningRun() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        Experiment experiment = experimentRepository.saveAndFlush(new Experiment(project, "baseline", null));
        User user = userRepository.saveAndFlush(
                new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, user, ProjectRole.EDITOR));
        UUID runId = runService.create(experiment.getId(), new CreateRunRequest(user.getId(), "training")).id();
        runService.start(runId);
        return runId;
    }

    private static CreateMetricRequest metric(String name, long step) {
        return new CreateMetricRequest(name, new BigDecimal("0.95000000"), step);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
