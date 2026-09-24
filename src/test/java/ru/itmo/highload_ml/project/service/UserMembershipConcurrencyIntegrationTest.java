package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.api.dto.AddMemberRequest;
import ru.itmo.highload_ml.project.api.dto.CreateProjectRequest;
import ru.itmo.highload_ml.project.exception.UserHasMembershipsException;
import ru.itmo.highload_ml.project.exception.UserNotFoundException;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembershipId;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UserMembershipConcurrencyIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMembershipService membershipService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @RepeatedTest(5)
    void addingMemberAndDeletingUserProduceConsistentResult() throws Exception {
        Project project = projectRepository.save(new Project("project-" + UUID.randomUUID(), null));
        User user = userRepository.save(new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> add = CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    membershipService.addMember(project.getId(), new AddMemberRequest(user.getId(), ProjectRole.EDITOR));
                    return "added";
                } catch (UserNotFoundException e) {
                    return "user-not-found";
                }
            }, executor);
            CompletableFuture<String> delete = CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    userService.delete(user.getId());
                    return "deleted";
                } catch (UserHasMembershipsException e) {
                    return "has-memberships";
                }
            }, executor);

            String addResult = add.get(20, TimeUnit.SECONDS);
            String deleteResult = delete.get(20, TimeUnit.SECONDS);
            boolean membershipExists = membershipRepository.existsById(new ProjectMembershipId(project.getId(), user.getId()));

            if ("added".equals(addResult)) {
                assertThat(deleteResult).isEqualTo("has-memberships");
                assertThat(userRepository.existsById(user.getId())).isTrue();
                assertThat(membershipExists).isTrue();
            } else {
                assertThat(addResult).isEqualTo("user-not-found");
                assertThat(deleteResult).isEqualTo("deleted");
                assertThat(userRepository.existsById(user.getId())).isFalse();
                assertThat(membershipExists).isFalse();
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @RepeatedTest(5)
    void creatingProjectWithOwnerAndDeletingUserProduceConsistentResult() throws Exception {
        User user = userRepository.save(new User("user-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        String projectName = "project-" + UUID.randomUUID();

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> create = CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    projectService.create(new CreateProjectRequest(projectName, null, user.getId()));
                    return "created";
                } catch (UserNotFoundException e) {
                    return "user-not-found";
                }
            }, executor);
            CompletableFuture<String> delete = CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    userService.delete(user.getId());
                    return "deleted";
                } catch (UserHasMembershipsException e) {
                    return "has-memberships";
                }
            }, executor);

            String createResult = create.get(20, TimeUnit.SECONDS);
            String deleteResult = delete.get(20, TimeUnit.SECONDS);

            if ("created".equals(createResult)) {
                assertThat(deleteResult).isEqualTo("has-memberships");
                assertThat(userRepository.existsById(user.getId())).isTrue();
                assertThat(projectRepository.existsByName(projectName)).isTrue();
                assertThat(membershipRepository.existsByIdUserId(user.getId())).isTrue();
            } else {
                assertThat(createResult).isEqualTo("user-not-found");
                assertThat(deleteResult).isEqualTo("deleted");
                assertThat(projectRepository.existsByName(projectName)).isFalse();
                assertThat(membershipRepository.existsByIdUserId(user.getId())).isFalse();
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
