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
        };
    }
}
