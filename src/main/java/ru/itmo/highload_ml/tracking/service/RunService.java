package ru.itmo.highload_ml.tracking.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.port.ExperimentAccessPort;
import ru.itmo.highload_ml.project.port.ProjectAccessPort;
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.api.dto.RunResponse;
import ru.itmo.highload_ml.tracking.exception.InvalidRunStateTransitionException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.mapper.RunMapper;
import ru.itmo.highload_ml.tracking.model.Run;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RunService {

    private static final String EXPERIMENT_FK = "fk_runs_experiment";

    private final ExperimentAccessPort experimentAccessPort;
    private final ProjectAccessPort projectAccessPort;
    private final RunRepository runRepository;
    private final RunMapper mapper;

    public RunService(ExperimentAccessPort experimentAccessPort, ProjectAccessPort projectAccessPort,
                      RunRepository runRepository, RunMapper mapper) {
        this.experimentAccessPort = experimentAccessPort;
        this.projectAccessPort = projectAccessPort;
        this.runRepository = runRepository;
        this.mapper = mapper;
    }

    /** Check project membership before insertion; the database FK protects a concurrent experiment delete. */
    @Transactional
    public RunResponse create(UUID experimentId, CreateRunRequest request) {
        UUID projectId = experimentAccessPort.getProjectId(experimentId);
        projectAccessPort.requireMember(projectId, request.authorId());
        try {
            Run run = runRepository.saveAndFlush(new Run(experimentId, request.authorId(), request.name()));
            return mapper.toResponse(run);
        } catch (DataIntegrityViolationException e) {
            if (hasConstraint(e, EXPERIMENT_FK)) {
                throw new ExperimentNotFoundException(experimentId);
            }
            throw e;
        }
    }

    public RunResponse getById(UUID runId) {
        return mapper.toResponse(runRepository.findById(runId)
                .orElseThrow(() -> new RunNotFoundException(runId)));
    }

    public Slice<RunResponse> findAll(UUID experimentId, RunCursor after, Pageable pageable) {
        experimentAccessPort.requireExists(experimentId);
        Slice<Run> runs = after == null
                ? runRepository.findByExperimentIdOrderByCreatedAtDescIdDesc(experimentId, pageable)
                : runRepository.findBeforeCursor(experimentId, after.createdAt(), after.id(), pageable);
        return runs.map(mapper::toResponse);
    }

    /** The run row lock serializes this transition with other transitions and future metric/artifact writes. */
    @Transactional
    public RunResponse start(UUID runId) {
        Run run = lockRun(runId);
        requireStatus(run, RunStatus.CREATED, RunStatus.RUNNING);
        run.setStartedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        run.setStatus(RunStatus.RUNNING);
        runRepository.flush();
        return mapper.toResponse(run);
    }

    @Transactional
    public RunResponse complete(UUID runId) {
        return finish(runId, RunStatus.COMPLETED);
    }

    @Transactional
    public RunResponse fail(UUID runId) {
        return finish(runId, RunStatus.FAILED);
    }

    private RunResponse finish(UUID runId, RunStatus target) {
        Run run = lockRun(runId);
        requireStatus(run, RunStatus.RUNNING, target);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // Keep the database invariant even if the system clock moves backwards between transitions.
        run.setFinishedAt(now.isBefore(run.getStartedAt()) ? run.getStartedAt() : now);
        run.setStatus(target);
        runRepository.flush();
        return mapper.toResponse(run);
    }

    private Run lockRun(UUID runId) {
        return runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
    }

    private static void requireStatus(Run run, RunStatus expected, RunStatus target) {
        if (run.getStatus() != expected) {
            throw new InvalidRunStateTransitionException(run.getId(), run.getStatus(), target);
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
}
