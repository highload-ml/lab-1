package ru.itmo.highload_ml.registry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RegistryVersionCounter {

    private final JdbcTemplate jdbcTemplate;

    public RegistryVersionCounter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Allocates the next project-local version number. */
    public long allocate(UUID projectId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO registry_version_counters (project_id, next_version)
                VALUES (?, 2)
                ON CONFLICT (project_id) DO UPDATE
                SET next_version = registry_version_counters.next_version + 1
                RETURNING next_version - 1
                """, Long.class, projectId);
    }

}
