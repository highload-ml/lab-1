package ru.itmo.highload_ml.tracking.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricRequest;
import ru.itmo.highload_ml.tracking.api.dto.CreateMetricsRequest;
import ru.itmo.highload_ml.tracking.api.dto.MetricResponse;
import ru.itmo.highload_ml.tracking.exception.DuplicateMetricException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.exception.RunNotRunningException;
import ru.itmo.highload_ml.tracking.mapper.MetricMapper;
import ru.itmo.highload_ml.tracking.model.Metric;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.MetricRepository;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MetricService {

    private static final String UNIQUE_METRIC_CONSTRAINT = "uk_metrics_run_name_step";

    private final RunRepository runRepository;
    private final MetricRepository metricRepository;
    private final MetricMapper mapper;

    public MetricService(RunRepository runRepository, MetricRepository metricRepository, MetricMapper mapper) {
        this.runRepository = runRepository;
        this.metricRepository = metricRepository;
        this.mapper = mapper;
    }

    @Transactional
    public MetricResponse log(UUID runId, CreateMetricRequest request) {
        return persistBatch(runId, List.of(request)).getFirst();
    }

    /** All metrics in a batch commit or roll back together; the run lock excludes concurrent completion. */
    @Transactional
    public List<MetricResponse> logBatch(UUID runId, CreateMetricsRequest request) {
        return persistBatch(runId, request.metrics());
    }

    public Page<MetricResponse> findAll(UUID runId, Pageable pageable) {
        if (!runRepository.existsById(runId)) {
            throw new RunNotFoundException(runId);
        }
        return metricRepository.findByRun_Id(runId, pageable).map(mapper::toResponse);
    }

    private List<MetricResponse> persistBatch(UUID runId, List<CreateMetricRequest> requests) {
        Run run = runRepository.findByIdForUpdate(runId).orElseThrow(() -> new RunNotFoundException(runId));
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new RunNotRunningException(runId, run.getStatus());
        }

        Set<MetricKey> keys = new HashSet<>();
        List<Metric> metrics = new ArrayList<>(requests.size());
        for (CreateMetricRequest request : requests) {
            MetricKey key = new MetricKey(request.name(), request.step());
            if (!keys.add(key) || metricRepository.existsByRun_IdAndNameAndStep(runId, key.name(), key.step())) {
                throw new DuplicateMetricException(runId, key.name(), key.step());
            }
            metrics.add(new Metric(run, request.name(), request.value(), request.step()));
        }

        try {
            return metricRepository.saveAllAndFlush(metrics).stream().map(mapper::toResponse).toList();
        } catch (DataIntegrityViolationException e) {
            if (hasConstraint(e, UNIQUE_METRIC_CONSTRAINT)) {
                throw new DuplicateMetricException(runId);
            }
            throw e;
        }
    }

    private static boolean hasConstraint(Throwable error, String constraintName) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private record MetricKey(String name, long step) {
    }
}
