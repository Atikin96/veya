package com.veya;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("The application connects as veya_app_user, which is not a superuser")
    void applicationDoesNotConnectAsSuperuser() {
        String user = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        Boolean superuser = jdbcTemplate.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);

        assertThat(user).isEqualTo(VeyaTestDatabase.APP_USER);
        assertThat(superuser).isFalse();
    }

    @Test
    @DisplayName("Flyway applied all migrations as veya_owner")
    void flywayAppliedAllMigrationsAsOwner() {
        JdbcTemplate owner = new JdbcTemplate(VeyaTestDatabase.dataSource(
                VeyaTestDatabase.shared(), VeyaTestDatabase.OWNER_USER, VeyaTestDatabase.OWNER_PASSWORD));

        Integer failed = owner.queryForObject(
                "SELECT count(*) FROM veya.flyway_schema_history WHERE NOT success", Integer.class);
        String installedBy = owner.queryForObject(
                "SELECT string_agg(DISTINCT installed_by, ',') FROM veya.flyway_schema_history", String.class);
        Integer baseline = owner.queryForObject(
                "SELECT count(*) FROM veya.flyway_schema_history WHERE version = '1' AND success", Integer.class);

        assertThat(failed).isZero();
        assertThat(baseline).isEqualTo(1);
        assertThat(installedBy).isEqualTo(VeyaTestDatabase.OWNER_USER);
    }
}
