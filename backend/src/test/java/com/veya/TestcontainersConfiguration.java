package com.veya;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Points the application at the shared PostgreSQL 17 test container ({@link VeyaTestDatabase}) with the
 * same role split as production: the datasource connects as veya_app_user and Flyway migrates as
 * veya_owner. Neither is a superuser. Import it into any test that needs the database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String JWT_SECRET = randomKey();

    private static String randomKey() {
        byte[] key = new byte[48];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    @Bean
    DynamicPropertyRegistrar veyaDatabaseProperties() {
        PostgreSQLContainer container = VeyaTestDatabase.shared();
        String url = VeyaTestDatabase.jdbcUrl(container);
        return registry -> {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> VeyaTestDatabase.APP_USER);
            registry.add("spring.datasource.password", () -> VeyaTestDatabase.APP_PASSWORD);
            registry.add("spring.flyway.url", () -> url);
            registry.add("spring.flyway.user", () -> VeyaTestDatabase.OWNER_USER);
            registry.add("spring.flyway.password", () -> VeyaTestDatabase.OWNER_PASSWORD);
            // Random signing key per test run (no secret in the repository); cheap bcrypt cost for fast tests.
            registry.add("veya.security.jwt-secret", () -> JWT_SECRET);
            registry.add("veya.security.bcrypt-strength", () -> "4");
        };
    }
}
