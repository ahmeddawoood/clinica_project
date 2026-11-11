package com.example.clinic.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Opt in only against a disposable MySQL database, never a deployment database.
@SpringBootTest
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "MIGRATION_DB_URL", matches = ".+")
class MySqlMigrationTest {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migratesEmptyDatabaseValidatesJpaAndPreservesDataOnRepeat() {
        // Context startup has already run V1 and Hibernate schema validation.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        jdbc.update("INSERT INTO app_user (email, password, role, enabled) VALUES (?, ?, ?, ?)",
                "migration-check@example.test", "fixture-hash", "PATIENT", true);
        try {
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?",
                    Integer.class, "migration-check@example.test")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                    Integer.class)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM app_user WHERE email = ?", "migration-check@example.test");
        }
    }
}
