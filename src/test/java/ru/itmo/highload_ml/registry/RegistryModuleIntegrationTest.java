package ru.itmo.highload_ml.registry;

import org.springframework.beans.factory.annotation.Autowired;
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
import ru.itmo.highload_ml.tracking.model.ArtifactType;
import ru.itmo.highload_ml.tracking.service.ArtifactService;
import ru.itmo.highload_ml.tracking.service.RunService;

import java.util.UUID;

public abstract class RegistryModuleIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ExperimentRepository experimentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;
    @Autowired private RunService runService;
    @Autowired private ArtifactService artifactService;

    protected Fixture createFixture() {
        Project project = projectRepository.saveAndFlush(new Project("project-" + UUID.randomUUID(), null));
        User user = userRepository.saveAndFlush(new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, user, ProjectRole.EDITOR));
        Experiment experiment = experimentRepository.saveAndFlush(new Experiment(project, "training", null));
        return new Fixture(project.getId(), experiment.getId(), user.getId());
    }

    protected UUID createArtifact(Fixture fixture, ArtifactType type, boolean completeRun) {
        UUID runId = runService.create(fixture.experimentId(),
                new CreateRunRequest(fixture.userId(), "run-" + UUID.randomUUID())).id();
        runService.start(runId);
        UUID artifactId = artifactService.register(runId, new CreateArtifactRequest(
                "artifact-" + UUID.randomUUID(), type, "models/weights.bin", 1024L)).id();
        if (completeRun) {
            runService.complete(runId);
        }
        return artifactId;
    }

    protected record Fixture(UUID projectId, UUID experimentId, UUID userId) {
    }
}
