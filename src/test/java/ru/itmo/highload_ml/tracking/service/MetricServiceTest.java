package ru.itmo.highload_ml.tracking.service;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricRequest;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricsRequest;
import ru.itmo.highload_ml.tracking.exception.DuplicateMetricException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.exception.RunNotRunningException;
import ru.itmo.highload_ml.tracking.mapper.MetricMapper;
import ru.itmo.highload_ml.tracking.model.Metric;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.MetricRepository;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricServiceTest {

    @Mock private RunRepository runRepository;
    @Mock private MetricRepository metricRepository;
    @Spy private MetricMapper mapper = new MetricMapper();
    @InjectMocks private MetricService service;

    @Test
    void logsSingleMetricAgainstLockedRunningRun() {
        Run run = run(RunStatus.RUNNING);
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(metricRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<Metric> metrics = invocation.getArgument(0);
            metrics.forEach(metric -> {
                ReflectionTestUtils.setField(metric, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(metric, "recordedAt", Instant.now());
            });
            return metrics;
        });

        var response = service.log(run.getId(), metric("accuracy", 1));

        assertThat(response.runId()).isEqualTo(run.getId());
        assertThat(response.name()).isEqualTo("accuracy");
        assertThat(response.value()).isEqualByComparingTo("0.95");
        assertThat(response.step()).isEqualTo(1);
        assertThat(response.recordedAt()).isNotNull();
        verify(metricRepository).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsCreatedOrCompletedRunWithoutSaving() {
        for (RunStatus status : List.of(RunStatus.CREATED, RunStatus.COMPLETED, RunStatus.FAILED)) {
            Run run = run(status);
            when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
            assertThatThrownBy(() -> service.log(run.getId(), metric("accuracy", 1)))
                    .isInstanceOf(RunNotRunningException.class);
        }
        verify(metricRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsDuplicateInsideBatchBeforeWriting() {
        Run run = run(RunStatus.RUNNING);
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.logBatch(run.getId(),
                new CreateMetricsRequest(List.of(metric("accuracy", 1), metric("accuracy", 1)))))
                .isInstanceOf(DuplicateMetricException.class);
        verify(metricRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsMetricAlreadyStoredInDatabase() {
        Run run = run(RunStatus.RUNNING);
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(metricRepository.existsByRun_IdAndNameAndStep(run.getId(), "accuracy", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.log(run.getId(), metric("accuracy", 1)))
                .isInstanceOf(DuplicateMetricException.class);
        verify(metricRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void translatesUniqueConstraintRaceToConflict() {
        Run run = run(RunStatus.RUNNING);
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("uk_metrics_run_name_step");
        when(metricRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("duplicate", violation));

        assertThatThrownBy(() -> service.log(run.getId(), metric("accuracy", 1)))
                .isInstanceOf(DuplicateMetricException.class);
    }

    @Test
    void missingRunIsNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.log(runId, metric("accuracy", 1)))
                .isInstanceOf(RunNotFoundException.class);
    }

    private static Run run(RunStatus status) {
        Run run = new Run(UUID.randomUUID(), UUID.randomUUID(), "training");
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setStatus(status);
        return run;
    }

    private static CreateMetricRequest metric(String name, long step) {
        return new CreateMetricRequest(name, new BigDecimal("0.95"), step);
    }
}
