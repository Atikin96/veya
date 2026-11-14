package com.veya;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VeyaApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void connectsToPostgresql17() {
        String version = jdbcTemplate.queryForObject("SHOW server_version", String.class);

        assertThat(version).startsWith("17.");
    }

    @Test
    void flywayAppliesBaselineMigrationOnStartup() {
        Boolean baselineApplied = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(baselineApplied).isTrue();
    }
}
