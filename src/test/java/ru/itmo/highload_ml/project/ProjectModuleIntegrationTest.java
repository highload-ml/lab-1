package ru.itmo.highload_ml.project;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.itmo.highload_ml.BaseIntegrationTest;

/**
 * Integration tests share one database; all tables related by foreign keys must be listed
 * in one TRUNCATE statement because PostgreSQL checks foreign keys even when child tables are empty.
 */
public abstract class ProjectModuleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void truncateProjectModuleTables() {
        jdbcTemplate.execute("TRUNCATE TABLE metrics, artifacts, runs, experiment_tags, experiments, tags, "
                + "project_memberships, projects, users");
    }
}
