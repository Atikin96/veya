package com.veya;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PostgreSQL 17 test database provisioned exactly like {@code infra/docker-compose.yml}: the container runs
 * {@code infra/postgres/initdb/01-create-roles.sh}, so tests use the real non-superuser roles
 * (veya_owner for Flyway, veya_app_user for the application, veya_readonly_user, veya_admin_user).
 * Passwords are random per test run and exist only inside throw-away containers.
 */
public final class VeyaTestDatabase {

    public static final String DATABASE = "veya";
    public static final String OWNER_USER = "veya_owner";
    public static final String OWNER_PASSWORD = randomPassword();
    public static final String APP_USER = "veya_app_user";
    public static final String APP_PASSWORD = randomPassword();
    public static final String READONLY_USER = "veya_readonly_user";
    public static final String READONLY_PASSWORD = randomPassword();
    public static final String ADMIN_USER = "veya_admin_user";
    public static final String ADMIN_PASSWORD = randomPassword();

    private static String randomPassword() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static PostgreSQLContainer shared;

    private VeyaTestDatabase() {
    }

    /** Container shared by all Spring test contexts in this JVM (started once, removed by Ryuk at exit). */
    public static synchronized PostgreSQLContainer shared() {
        if (shared == null) {
            shared = newContainer();
            shared.start();
        }
        return shared;
    }

    /** A new, not yet started container (used to prove reproducibility from a clean environment). */
    public static PostgreSQLContainer newContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName(DATABASE)
                .withUsername("postgres")
                .withPassword(randomPassword())
                .withEnv("VEYA_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("VEYA_APP_PASSWORD", APP_PASSWORD)
                .withEnv("VEYA_READONLY_PASSWORD", READONLY_PASSWORD)
                .withEnv("VEYA_ADMIN_PASSWORD", ADMIN_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(repositoryFile("infra/postgres/initdb/01-create-roles.sh"), 0755),
                        "/docker-entrypoint-initdb.d/01-create-roles.sh")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(repositoryFile("infra/postgres/seed/demo_data.sql")),
                        "/veya/seed/demo_data.sql");
    }

    public static String jdbcUrl(PostgreSQLContainer container) {
        String url = container.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=veya";
    }

    public static DriverManagerDataSource dataSource(PostgreSQLContainer container, String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl(container), user, password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    /** Applies the Flyway migrations as veya_owner (same settings as application.yml). */
    public static void migrate(PostgreSQLContainer container) {
        Flyway.configure()
                .dataSource(jdbcUrl(container), OWNER_USER, OWNER_PASSWORD)
                .schemas("veya")
                .defaultSchema("veya")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /** Resolves a file relative to the repository root, independent of the working directory. */
    public static Path repositoryFile(String relativePath) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Repository file not found: " + relativePath);
    }
}
