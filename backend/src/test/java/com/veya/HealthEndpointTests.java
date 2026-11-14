package com.veya;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the full application on a random port against a real PostgreSQL container
 * and checks that the health/readiness endpoints answer over HTTP and that sensitive actuator
 * endpoints are not exposed. Supports US-32 / NFR-17 (readiness after migrations) but does not by
 * itself verify the Docker Compose startup or the NFR-11 availability probe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HealthEndpointTests {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthEndpointReportsUp(String path) {
        ResponseEntity<String> response = restClient.get().uri(path).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops"})
    void sensitiveActuatorEndpointsAreNotExposed(String path) {
        HttpStatus status = restClient.get().uri(path)
                .exchange((request, response) -> HttpStatus.valueOf(response.getStatusCode().value()));

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
