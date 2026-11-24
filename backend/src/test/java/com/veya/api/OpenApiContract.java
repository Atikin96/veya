package com.veya.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.veya.VeyaTestDatabase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The published contract docs/api/openapi.yaml, used to check that real responses match it: the status code must be
 * documented for the operation and the body must validate against the documented schema (OpenAPI 3.1 schemas are
 * JSON Schema 2020-12; formats such as uuid, date and date-time are asserted).
 */
public final class OpenApiContract {

    private static final OpenApiContract INSTANCE = new OpenApiContract();

    private final JsonNode spec;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    private OpenApiContract() {
        try {
            spec = new YAMLMapper().readTree(VeyaTestDatabase.repositoryFile("docs/api/openapi.yaml").toFile());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static OpenApiContract get() {
        return INSTANCE;
    }

    public JsonNode spec() {
        return spec;
    }

    public JsonNode operation(String method, String pathTemplate) {
        JsonNode op = spec.path("paths").path(pathTemplate).path(method.toLowerCase(Locale.ROOT));
        if (op.isMissingNode()) {
            throw new AssertionError("Operation " + method + " " + pathTemplate + " is not in docs/api/openapi.yaml");
        }
        return op;
    }

    /** @return validation problems (empty = the response matches the contract). */
    public List<String> check(String method, String pathTemplate, int status, String contentType, String body) {
        List<String> problems = new ArrayList<>();
        JsonNode response = resolve(operation(method, pathTemplate).path("responses").path(String.valueOf(status)));
        if (response.isMissingNode()) {
            problems.add("status " + status + " is not documented for " + method + " " + pathTemplate);
            return problems;
        }
        JsonNode content = response.path("content");
        if (content.isMissingNode() || content.isEmpty()) {
            if (body != null && !body.isEmpty()) {
                problems.add("status " + status + " documents no body, but a body was returned");
            }
            return problems;
        }
        String mediaType = contentType == null ? "" : contentType.split(";")[0].trim();
        JsonNode media = content.path(mediaType);
        if (media.isMissingNode()) {
            problems.add("content type " + mediaType + " is not documented (documented: " + names(content) + ")");
            return problems;
        }
        JsonNode schemaNode = media.path("schema");
        if (schemaNode.isMissingNode()) {
            return problems;
        }
        String key = method + " " + pathTemplate + " " + status + " " + mediaType;
        JsonSchema schema = cache.computeIfAbsent(key, k -> {
            ObjectNode root = ((ObjectNode) schemaNode.deepCopy());
            root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
            root.set("components", spec.path("components"));
            return factory.getSchema(root, config);
        });
        Set<ValidationMessage> errors = schema.validate(body, InputFormat.JSON);
        errors.forEach(e -> problems.add(e.getMessage()));
        return problems;
    }

    private JsonNode resolve(JsonNode node) {
        while (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            node = spec.at(ref.substring(1));
        }
        return node;
    }

    private static String names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.properties().forEach(e -> names.add(e.getKey()));
        return String.join(", ", names);
    }
}
