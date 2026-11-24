package com.veya.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.veya.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the code and the published contract in sync (US-29, NFR-15): every REST handler is documented in
 * docs/api/openapi.yaml, every operation marked IMPLEMENTED or PARTIAL has a handler, and no PLANNED/BLOCKED
 * operation has one (so the contract never under- or over-states what works).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OpenApiContractTests {

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Handlers and x-veya-status of docs/api/openapi.yaml match exactly")
    void handlersMatchTheContract() {
        Set<String> handlers = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getPackageName().startsWith("com.veya")) {
                return;
            }
            for (String pattern : info.getPatternValues()) {
                info.getMethodsCondition().getMethods().forEach(m -> handlers.add(m.name() + " " + pattern));
            }
        });

        Set<String> served = new TreeSet<>();
        Set<String> notServed = new TreeSet<>();
        JsonNode paths = OpenApiContract.get().spec().get("paths");
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            if (path.getKey().startsWith("/actuator")) {
                continue; // served by Spring Boot Actuator, covered by HealthEndpointTests
            }
            for (String method : METHODS) {
                JsonNode op = path.getValue().get(method);
                if (op == null) {
                    continue;
                }
                String key = method.toUpperCase() + " " + path.getKey();
                String status = op.get("x-veya-status").asText();
                (status.equals("IMPLEMENTED") || status.equals("PARTIAL") ? served : notServed).add(key);
            }
        }

        assertThat(handlers).as("every handler is documented as IMPLEMENTED/PARTIAL").isEqualTo(served);
        assertThat(notServed).as("no PLANNED/BLOCKED operation has a handler").noneMatch(handlers::contains);
    }
}
