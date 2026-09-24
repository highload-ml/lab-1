package ru.itmo.highload_ml.project;

import org.springframework.test.util.ReflectionTestUtils;
import ru.itmo.highload_ml.project.model.Project;
import ru.itmo.highload_ml.project.model.ProjectMembership;
import ru.itmo.highload_ml.project.model.ProjectRole;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;

import java.util.UUID;

/**
 * Detached entities with ids assigned, for unit tests that never touch the database.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static User user(String nickname) {
        User user = new User(nickname, "hash", UserRole.ML_ENGINEER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    public static Project project(String name) {
        Project project = new Project(name, "description");
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        return project;
    }

    public static ProjectMembership membership(Project project, User user, ProjectRole role) {
        return new ProjectMembership(project, user, role);
    }
}
