package ru.itmo.highload_ml.project.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.itmo.highload_ml.project.ProjectModuleIntegrationTest;
import ru.itmo.highload_ml.project.model.User;
import ru.itmo.highload_ml.project.model.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryIntegrationTest extends ProjectModuleIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsUserWithGeneratedIdCreationTimeAndRoleAsString() {
        User saved = userRepository.saveAndFlush(new User("alice", "hash", UserRole.ML_ENGINEER));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        String storedRole = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, saved.getId());
        assertThat(storedRole).isEqualTo("ML_ENGINEER");
    }

    @Test
    void existsByNicknameFindsOnlyExactNickname() {
        userRepository.saveAndFlush(new User("alice", "hash", UserRole.ADMIN));

        assertThat(userRepository.existsByNickname("alice")).isTrue();
        assertThat(userRepository.existsByNickname("bob")).isFalse();
    }

    @Test
    void databaseEnforcesUniqueNickname() {
        userRepository.saveAndFlush(new User("alice", "hash", UserRole.ADMIN));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("alice", "other", UserRole.REVIEWER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownRole() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (id, nickname, password_hash, role, created_at) "
                        + "VALUES (gen_random_uuid(), 'mallory', 'hash', 'ROOT', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listOrderingIsBackedByIndex() {
        Integer indexes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'ix_users_created_at_id'",
                Integer.class);

        assertThat(indexes).isEqualTo(1);
    }
}
