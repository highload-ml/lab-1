package ru.itmo.highload_ml.project;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.itmo.highload_ml.BaseIntegrationTest;

/**
 * Integration tests of the project module share one database; truncating all module tables together
 * keeps foreign keys between users, projects and memberships from breaking cleanup order.
 */
public abstract class ProjectModuleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void truncateProjectModuleTables() {
        jdbcTemplate.execute("TRUNCATE TABLE project_memberships, projects, users");
    }
}
