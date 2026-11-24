package com.veya.shared.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * US-29 / NFR-15: serves the published contract {@code docs/api/openapi.yaml} (single source of truth, packaged into
 * the jar at build time) as JSON. Nothing is generated from the code, so the served document is always identical
 * to the contract shared with the frontend; {@code OpenApiContractTests} checks that the controllers match it.
 */
@RestController
public class OpenApiDocsController {

    static final String CONTRACT = "openapi/openapi.yaml";

    private final JsonNode document;

    public OpenApiDocsController() {
        try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
            this.document = YAMLMapper.builder().build().readTree(in);
        } catch (IOException ex) {
            throw new UncheckedIOException("OpenAPI contract " + CONTRACT + " is missing from the build", ex);
        }
    }

    @GetMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode apiDocs() {
        return document;
    }
}
