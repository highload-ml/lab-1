package ru.itmo.highload_ml.tracking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.exception.ExperimentNotFoundException;
import ru.itmo.highload_ml.project.exception.NotProjectMemberException;
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
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.exception.InvalidRunStateTransitionException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.RunRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunServiceIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private RunService runService;
    @Autowired private RunRepository runRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;

    @Test
    void createsStartsAndCompletesRun() {
        Context context = contextWithMember();
        var created = runService.create(context.experimentId(), new CreateRunRequest(context.userId(), "training"));

        assertThat(created.status()).isEqualTo(RunStatus.CREATED);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.startedAt()).isNull();
        assertThat(created.finishedAt()).isNull();

        var started = runService.start(created.id());
        assertThat(started.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(started.startedAt()).isNotNull();
        assertThat(started.finishedAt()).isNull();

        var completed = runService.complete(created.id());
        assertThat(completed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(completed.finishedAt()).isAfterOrEqualTo(completed.startedAt());
        assertThat(runService.getById(created.id())).isEqualTo(completed);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM runs WHERE id = ?", String.class, created.id()))
                .isEqualTo("COMPLETED");
        assertThatThrownBy(() -> runService.fail(created.id()))
                .isInstanceOf(InvalidRunStateTransitionException.class);
    }

    @Test
    void failedRunIsTerminalAndInvalidTransitionsDoNotChangeDatabase() {
        Context context = contextWithMember();
        UUID runId = runService.create(context.experimentId(),
                new CreateRunRequest(context.userId(), "training")).id();

        assertThatThrownBy(() -> runService.complete(runId))
                .isInstanceOf(InvalidRunStateTransitionException.class);
        assertThat(runService.getById(runId).status()).isEqualTo(RunStatus.CREATED);

        runService.start(runId);
        var failed = runService.fail(runId);
        assertThat(failed.status()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.finishedAt()).isNotNull();
        assertThatThrownBy(() -> runService.start(runId))
                .isInstanceOf(InvalidRunStateTransitionException.class);
        assertThat(runService.getById(runId).status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void rejectsUnknownExperimentAndNonMember() {
        Context context = contextWithMember();
        assertThatThrownBy(() -> runService.create(UUID.randomUUID(),
                new CreateRunRequest(context.userId(), "training")))
                .isInstanceOf(ExperimentNotFoundException.class);
        assertThatThrownBy(() -> runService.create(context.experimentId(),
                new CreateRunRequest(UUID.randomUUID(), "training")))
                .isInstanceOf(NotProjectMemberException.class);
        assertThat(runRepository.count()).isZero();
        assertThatThrownBy(() -> runService.getById(UUID.randomUUID()))
                .isInstanceOf(RunNotFoundException.class);
    }

    private Context contextWithMember() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        Experiment experiment = experimentRepository.saveAndFlush(new Experiment(project, "baseline", null));
        User user = userRepository.saveAndFlush(
                new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, user, ProjectRole.EDITOR));
        return new Context(experiment.getId(), user.getId());
    }

    private record Context(UUID experimentId, UUID userId) {
    }
}
