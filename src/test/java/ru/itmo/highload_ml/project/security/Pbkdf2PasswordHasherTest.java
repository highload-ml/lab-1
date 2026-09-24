package ru.itmo.highload_ml.project.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void hashDoesNotContainRawPasswordAndIsSalted() {
        String first = hasher.hash("password123");
        String second = hasher.hash("password123");

        assertThat(first).startsWith("pbkdf2$").doesNotContain("password123");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void matchesOnlyOriginalPassword() {
        String hash = hasher.hash("password123");

        assertThat(hasher.matches("password123", hash)).isTrue();
        assertThat(hasher.matches("password124", hash)).isFalse();
    }

    @Test
    void rejectsUnknownHashFormat() {
        assertThat(hasher.matches("password123", "plain-text")).isFalse();
    }
}
