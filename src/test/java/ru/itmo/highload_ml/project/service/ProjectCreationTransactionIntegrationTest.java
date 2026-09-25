package ru.itmo.highload_ml.project.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.api.dto.CreateProjectRequest;
import ru.itmo.highload_ml.project.api.dto.ProjectResponse;
import ru.itmo.highload_ml.project.exception.ProjectNameAlreadyTakenException;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;
import ru.itmo.highload_ml.project.repository.ProjectMembershipRepository;
import ru.itmo.highload_ml.project.repository.UserRepository;

import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Checks the transactional guarantees of {@link ProjectService#create} against a real database.
 */
class ProjectCreationTransactionIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private ProjectMembershipRepository membershipRepository;

    @Test
    void membershipFailureRollsBackProject() {
        User owner = userRepository.save(new User("alice", "hash", UserRole.ML_ENGINEER));
        doThrow(new IllegalStateException("simulated failure on step 4"))
                .when(membershipRepository).saveAndFlush(any(ProjectMembership.class));

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest("fraud", null, owner.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated failure on step 4");

        assertThat(countRows("SELECT COUNT(*) FROM projects WHERE name = 'fraud'")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM project_memberships")).isZero();
    }

    @Test
    void successfulCreationCommitsProjectAndOwnerTogether() {
        User owner = userRepository.save(new User("alice", "hash", UserRole.ML_ENGINEER));

        ProjectResponse created = projectService.create(new CreateProjectRequest("fraud", null, owner.getId()));

        assertThat(countRows("SELECT COUNT(*) FROM projects WHERE name = 'fraud'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM project_memberships WHERE project_id = ? AND user_id = ?",
                String.class, created.id(), owner.getId()))
                .isEqualTo(ProjectRole.OWNER.name());
    }

    /**
     * Different owners, so the owner-row lock does not serialize the two calls and both can pass the name
     * pre-check: the unique constraint must still let exactly one of them commit.
     */
    @RepeatedTest(5)
    void concurrentCreationWithSameNameCommitsExactlyOneProject() throws Exception {
        User alice = userRepository.save(new User("alice-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        User bob = userRepository.save(new User("bob-" + UUID.randomUUID(), "hash", UserRole.ML_ENGINEER));
        String projectName = "project-" + UUID.randomUUID();

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> first = createAsync(executor, start, projectName, alice.getId());
            CompletableFuture<String> second = createAsync(executor, start, projectName, bob.getId());

            assertThat(new String[]{first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder("created", "conflict");
            assertThat(countRows("SELECT COUNT(*) FROM projects WHERE name = '" + projectName + "'")).isEqualTo(1);
            assertThat(countRows("SELECT COUNT(*) FROM project_memberships")).isEqualTo(1);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private CompletableFuture<String> createAsync(ExecutorService executor, CyclicBarrier start,
                                                  String projectName, UUID ownerId) {
        return CompletableFuture.supplyAsync(() -> {
            await(start);
            try {
                projectService.create(new CreateProjectRequest(projectName, null, ownerId));
                return "created";
            } catch (ProjectNameAlreadyTakenException e) {
                return "conflict";
            }
        }, executor);
    }

    private int countRows(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }
}
