package com.veya.api;

import com.veya.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class of the REST API tests: the full application runs on a random port against PostgreSQL 17
 * (Testcontainers, real roles), and every call goes over real HTTP. {@link #expect} checks the status and validates the
 * response against docs/api/openapi.yaml, so each test is also a contract test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String PASSWORD = "correct-horse-battery-" + UUID.randomUUID().toString().substring(0, 8);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected record Response(int status, String contentType, String body, HttpResponse<String> raw) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }
    }

    /** A registered and signed-in user. */
    protected record TestUser(UUID id, String email, String token) {
    }

    protected Response call(String method, String path, String token, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                builder.header("Content-Type", "application/json");
                publisher = HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : JSON.writeValueAsString(body));
            }
            HttpResponse<String> response = HTTP.send(builder.method(method, publisher).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                    response.body(), response);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** Calls the operation, asserts the status and validates the response against the OpenAPI contract. */
    protected Response expect(int status, String method, String pathTemplate, String path, String token, Object body) {
        Response response = call(method, path, token, body);
        assertThat(response.status()).as("%s %s -> %s", method, path, response.body()).isEqualTo(status);
        List<String> problems = OpenApiContract.get().check(method, pathTemplate, status, response.contentType(), response.body());
        assertThat(problems).as("response of %s %s does not match docs/api/openapi.yaml: %s", method, path, response.body())
                .isEmpty();
        return response;
    }

    protected Response expect(int status, String method, String path, String token, Object body) {
        return expect(status, method, path.replaceAll("\\?.*$", ""), path, token, body);
    }

    protected static String uniqueEmail(String name) {
        return name + "." + UUID.randomUUID().toString().substring(0, 12) + "@example.org";
    }

    protected static Map<String, Object> registration(String email) {
        return Map.of("email", email, "password", PASSWORD, "adultConfirmed", true, "consentDocumentIds", List.of(1, 2));
    }

    protected TestUser newUser(String name) {
        String email = uniqueEmail(name);
        Response registered = expect(201, "POST", "/api/v1/auth/register", null, registration(email));
        Response login = expect(200, "POST", "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD));
        return new TestUser(UUID.fromString(registered.json().get("userId").asString()), email,
                login.json().get("accessToken").asString());
    }

    protected static void assertProblem(Response response, int status, String code) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat(response.contentType()).startsWith("application/problem+json");
        JsonNode json = response.json();
        assertThat(json.get("code").asString()).isEqualTo(code);
        assertThat(json.get("status").asInt()).isEqualTo(status);
        assertThat(json.get("type").asString()).startsWith("https://veya.example/problems/");
    }
}
