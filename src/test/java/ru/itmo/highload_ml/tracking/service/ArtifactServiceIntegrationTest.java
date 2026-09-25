package ru.itmo.highload_ml.tracking.service;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import ru.itmo.highload_ml.tracking.api.dto.CreateArtifactRequest;
import ru.itmo.highload_ml.tracking.api.dto.CreateRunRequest;
import ru.itmo.highload_ml.tracking.exception.ArtifactNameAlreadyTakenException;
import ru.itmo.highload_ml.tracking.exception.ArtifactNotFoundException;
import ru.itmo.highload_ml.tracking.exception.RunNotAcceptingArtifactsException;
import ru.itmo.highload_ml.tracking.exception.RunNotFoundException;
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.model.RunStatus;
import ru.itmo.highload_ml.tracking.repository.ArtifactRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactServiceIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private ArtifactService artifactService;
    @Autowired private RunService runService;
    @Autowired private ArtifactRepository artifactRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;
    @Autowired private Validator validator;

    @Test
    void registersAndReadsMetadataWithinRun() {
        UUID runId = createRunningRun();
        UUID otherRunId = createRunningRun();
        var created = artifactService.register(runId, request());

        assertThat(created.id()).isNotNull();
        assertThat(created.runId()).isEqualTo(runId);
        assertThat(created.type()).isEqualTo(ArtifactType.MODEL);
        assertThat(created.path()).isEqualTo("models/weights.bin");
        assertThat(created.sizeBytes()).isEqualTo(1024);
        assertThat(created.createdAt()).isNotNull();
        assertThat(artifactService.getById(runId, created.id())).isEqualTo(created);
        assertThatThrownBy(() -> artifactService.getById(otherRunId, created.id()))
                .isInstanceOf(ArtifactNotFoundException.class);
        assertThat(artifactService.register(otherRunId, request()).name()).isEqualTo("weights");

        var page = artifactService.findAll(runId, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).containsExactly(created);
        assertThatThrownBy(() -> artifactService.findAll(UUID.randomUUID(), PageRequest.of(0, 10)))
                .isInstanceOf(RunNotFoundException.class);
    }

    @Test
    void duplicateNameIsConflictAndCompletedRunRejectsNewArtifact() {
        UUID runId = createRunningRun();
        artifactService.register(runId, request());

        assertThatThrownBy(() -> artifactService.register(runId, request()))
                .isInstanceOf(ArtifactNameAlreadyTakenException.class);
        assertThat(artifactRepository.count()).isEqualTo(1);

        runService.complete(runId);
        assertThatThrownBy(() -> artifactService.register(runId,
                new CreateArtifactRequest("new", ArtifactType.LOG, "logs/output.txt", 5L)))
                .isInstanceOf(RunNotAcceptingArtifactsException.class);
        assertThat(artifactRepository.count()).isEqualTo(1);
    }

    @Test
    void requestRejectsMissingOrInvalidMetadata() {
        var violations = validator.validate(new CreateArtifactRequest(" ", null, " ", -1L));
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "type", "path", "sizeBytes");
        assertThat(validator.validate(new CreateArtifactRequest(
                "weights", ArtifactType.MODEL, "x".repeat(2049), 0L)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("path");
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

    private static CreateArtifactRequest request() {
        return new CreateArtifactRequest("weights", ArtifactType.MODEL, "models/weights.bin", 1024L);
    }
}
