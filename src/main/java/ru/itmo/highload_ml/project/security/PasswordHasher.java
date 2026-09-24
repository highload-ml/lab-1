package ru.itmo.highload_ml.project.security;

/**
 * Seam for password hashing. Replaced by Spring Security's BCrypt PasswordEncoder in lab 3.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);
}
