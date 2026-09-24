package ru.itmo.highload_ml.tracking.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Experiment;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.repository.ExperimentRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.tracking.model.Run;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunRepositoryLockIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private RunRepository runRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void secondWriterWaitsForFirstRunRowLock() throws Exception {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        Experiment experiment = experimentRepository.saveAndFlush(new Experiment(project, "baseline", null));
        UUID runId = runRepository.saveAndFlush(new Run(experiment.getId(), UUID.randomUUID(), "training")).getId();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                runRepository.findByIdForUpdate(runId).orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                secondStarted.countDown();
                runRepository.findByIdForUpdate(runId).orElseThrow();
            }));
            try {
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                releaseFirst.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the first run lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the run lock", e);
        }
    }
}
