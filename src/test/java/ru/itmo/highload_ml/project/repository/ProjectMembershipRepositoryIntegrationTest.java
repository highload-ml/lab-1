package ru.itmo.highload_ml.project.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectMembershipId;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectMembershipRepositoryIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsMembershipWithCompositeIdAndRoleAsString() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        User alice = userRepository.saveAndFlush(new User("alice", "hash", UserRole.ML_ENGINEER));

        ProjectMembership saved = membershipRepository.saveAndFlush(
                new ProjectMembership(project, alice, ProjectRole.OWNER));

        assertThat(saved.getId()).isEqualTo(new ProjectMembershipId(project.getId(), alice.getId()));
        assertThat(saved.getJoinedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM project_memberships WHERE project_id = ? AND user_id = ?",
                String.class, project.getId(), alice.getId()))
                .isEqualTo("OWNER");
    }

    @Test
    void findsMembersOfProjectWithUserAndCountsOwners() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        Project other = projectRepository.saveAndFlush(new Project("other", null));
        User alice = userRepository.saveAndFlush(new User("alice", "hash", UserRole.ML_ENGINEER));
        User bob = userRepository.saveAndFlush(new User("bob", "hash", UserRole.REVIEWER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, alice, ProjectRole.OWNER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, bob, ProjectRole.VIEWER));
        membershipRepository.saveAndFlush(new ProjectMembership(other, bob, ProjectRole.OWNER));

        var page = membershipRepository.findByIdProjectId(
                project.getId(), PageRequest.of(0, 10, Sort.by("joinedAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(m -> m.getUser().getNickname()).containsExactly("alice", "bob");
        assertThat(membershipRepository.countByIdProjectIdAndRole(project.getId(), ProjectRole.OWNER)).isEqualTo(1);
        assertThat(membershipRepository.existsByIdUserId(bob.getId())).isTrue();
    }

    @Test
    void deletingProjectRowCascadesToMemberships() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        User alice = userRepository.saveAndFlush(new User("alice", "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, alice, ProjectRole.OWNER));

        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", project.getId());

        assertThat(membershipRepository.count()).isZero();
    }

    @Test
    void databaseRefusesToDeleteUserWithMemberships() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        User alice = userRepository.saveAndFlush(new User("alice", "hash", UserRole.ML_ENGINEER));
        membershipRepository.saveAndFlush(new ProjectMembership(project, alice, ProjectRole.OWNER));

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", alice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownProjectRole() {
        Project project = projectRepository.saveAndFlush(new Project("fraud", null));
        User alice = userRepository.saveAndFlush(new User("alice", "hash", UserRole.ML_ENGINEER));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO project_memberships (project_id, user_id, role, joined_at) VALUES (?, ?, 'ROOT', now())",
                project.getId(), alice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
