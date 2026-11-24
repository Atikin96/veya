package com.veya.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared/system operations: /v3/api-docs (US-29), reference data, error format of unknown routes, CORS. */
class SystemApiTests extends ApiTestSupport {

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    @DisplayName("US-29: /v3/api-docs serves exactly the published contract docs/api/openapi.yaml as JSON, without a token")
    void apiDocsServeThePublishedContract() {
        Response response = expect(200, "GET", "/v3/api-docs", null, null);

        assertThat(response.contentType()).startsWith("application/json");
        assertThat(JSON.readTree(response.body()).toString())
                .isEqualTo(JSON.readTree(OpenApiContract.get().spec().toString()).toString());
    }

    @Test
    @DisplayName("The contract check used by all API tests rejects undocumented statuses and bodies that break the schema")
    void contractCheckDetectsMismatches() {
        OpenApiContract contract = OpenApiContract.get();
        assertThat(contract.check("GET", "/api/v1/me", 418, "application/json", "{}")).isNotEmpty();
        assertThat(contract.check("GET", "/api/v1/me", 200, "application/json", "{\"userId\":\"not-a-uuid\"}")).isNotEmpty();
        assertThat(contract.check("GET", "/api/v1/me", 401, "application/problem+json",
                "{\"type\":\"https://veya.example/problems/x\",\"title\":\"t\",\"status\":401,\"code\":\"NOT_A_CODE\"}")).isNotEmpty();
        assertThat(contract.check("GET", "/api/v1/me", 200, "text/plain", "x")).isNotEmpty();
    }

    @Test
    @DisplayName("Reference data lists the codes of the reference tables and requires authentication")
    void referenceData() {
        TestUser user = newUser("ref");
        Response response = expect(200, "GET", "/api/v1/reference-data", user.token(), null);
        assertThat(response.json().get("goals").size()).isEqualTo(3);
        assertThat(response.json().get("mealSlots").get(0).get("code").asString()).isEqualTo("BREAKFAST");
        assertThat(response.json().get("ingredients").size()).isEqualTo(21);

        assertProblem(expect(401, "GET", "/api/v1/reference-data", null, null), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("Unknown routes and unsupported methods also answer with Problem Details")
    void unknownRoutes() {
        TestUser user = newUser("routes");
        Response notFound = call("GET", "/api/v1/does-not-exist", user.token(), null);
        assertProblem(notFound, 404, "RESOURCE_NOT_FOUND");

        Response notAllowed = call("PATCH", "/api/v1/me", user.token(), "{}");
        assertThat(notAllowed.status()).isEqualTo(405);
        assertThat(notAllowed.contentType()).startsWith("application/problem+json");

        Response unauthenticated = call("GET", "/api/v1/does-not-exist", null, null);
        assertProblem(unauthenticated, 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("CORS: the React dev origin may call the API with a bearer token; other origins are refused")
    void corsPreflight() throws Exception {
        HttpResponse<String> allowed = preflight("http://localhost:5173");
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase()).contains("authorization");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();

        HttpResponse<String> refused = preflight("https://evil.example");
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me/profile"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
