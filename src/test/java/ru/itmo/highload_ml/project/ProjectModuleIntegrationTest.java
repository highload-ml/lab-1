package ru.itmo.highload_ml.project;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.itmo.highload_ml.BaseIntegrationTest;

/**
 * Integration tests of the project module share one database; all related tables must be listed
 * in one TRUNCATE statement because PostgreSQL checks foreign keys even when child tables are empty.
 */
public abstract class ProjectModuleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void truncateProjectModuleTables() {
        jdbcTemplate.execute("TRUNCATE TABLE experiment_tags, experiments, tags, project_memberships, projects, users");
    }
}
