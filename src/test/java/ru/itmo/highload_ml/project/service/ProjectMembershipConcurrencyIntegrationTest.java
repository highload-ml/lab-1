package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.exception.LastOwnerRemovalException;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.ProjectRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectMembershipConcurrencyIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private ProjectMembershipService membershipService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    /**
     * Without the project row lock both transactions would read "2 owners" and both delete, leaving none.
     */
    @RepeatedTest(5)
    void concurrentRemovalOfTwoOwnersKeepsExactlyOne() throws Exception {
        Project project = projectRepository.save(new Project("fraud-" + UUID.randomUUID(), null));
        User alice = userRepository.save(new User("alice-" + UUID.randomUUID(), "hash", UserRole.ADMIN));
        User bob = userRepository.save(new User("bob-" + UUID.randomUUID(), "hash", UserRole.ADMIN));
        membershipRepository.save(new ProjectMembership(project, alice, ProjectRole.OWNER));
        membershipRepository.save(new ProjectMembership(project, bob, ProjectRole.OWNER));

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Boolean>> results = List.of(alice, bob).stream()
                    .map(user -> CompletableFuture.supplyAsync(() -> tryRemove(start, project.getId(), user.getId()), executor))
                    .toList();

            long removed = results.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

            assertThat(removed).isEqualTo(1);
            assertThat(membershipRepository.countByIdProjectIdAndRole(project.getId(), ProjectRole.OWNER)).isEqualTo(1);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean tryRemove(CyclicBarrier start, UUID projectId, UUID userId) {
        try {
            start.await(10, TimeUnit.SECONDS);
            membershipService.removeMember(projectId, userId);
            return true;
        } catch (LastOwnerRemovalException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
