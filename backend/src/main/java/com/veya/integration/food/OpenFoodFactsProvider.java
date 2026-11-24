package com.veya.integration.food;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Open Food Facts adapter (public API, no key; data licence ODbL - attribution is returned with every product).
 * Disabled by default ({@code VEYA_FOOD_PROVIDER_ENABLED=false}): the BA names no specific provider, so enabling it is a
 * deployment decision (AQ-7). Products without complete or plausible per-100 g values are skipped, because the
 * local catalogue's database constraints (kcal 0-900, macros 0-100 g, sum <= 100 g) apply to cached provider rows too.
 */
@Component
public class OpenFoodFactsProvider implements FoodProvider {

    private static final String FIELDS = "code,product_name,nutriments";

    private final boolean enabled;
    private final URI baseUrl;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public OpenFoodFactsProvider(@Value("${veya.food-provider.enabled:false}") boolean enabled,
                                 @Value("${veya.food-provider.base-url:https://world.openfoodfacts.org}") String baseUrl,
                                 @Value("${veya.food-provider.timeout:PT2S}") Duration timeout) {
        this.enabled = enabled;
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String attribution() {
        return "Open Food Facts (openfoodfacts.org), ODbL";
    }

    @Override
    public List<ProviderFood> search(String text, int limit) {
        URI uri = baseUrl.resolve("cgi/search.pl?search_simple=1&json=1&page_size=" + limit + "&fields=" + FIELDS
                + "&search_terms=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("User-Agent", "VEYA/0.1 (educational project)").header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            throw new ProviderTimeoutException("Food provider timed out", ex);
        } catch (IOException ex) {
            throw new ProviderUnavailableException("Food provider is not reachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new ProviderUnavailableException("Food provider answered " + response.statusCode(), null);
        }
        try {
            return parse(json.readTree(response.body()), limit);
        } catch (RuntimeException ex) {
            throw new ProviderUnavailableException("Food provider returned an invalid document", ex);
        }
    }

    static List<ProviderFood> parse(JsonNode root, int limit) {
        List<ProviderFood> foods = new ArrayList<>();
        for (JsonNode product : root.path("products")) {
            String code = product.path("code").asString("").trim();
            String name = product.path("product_name").asString("").trim();
            JsonNode n = product.path("nutriments");
            BigDecimal kcal = number(n, "energy-kcal_100g");
            BigDecimal protein = number(n, "proteins_100g");
            BigDecimal fat = number(n, "fat_100g");
            BigDecimal carbs = number(n, "carbohydrates_100g");
            if (code.isEmpty() || code.length() > 100 || name.isEmpty() || kcal == null || protein == null || fat == null
                    || carbs == null) {
                continue;
            }
            boolean plausible = kcal.compareTo(BigDecimal.valueOf(900)) <= 0 && protein.add(fat).add(carbs)
                    .compareTo(BigDecimal.valueOf(100)) <= 0;
            if (plausible) {
                foods.add(new ProviderFood(code, name.length() > 200 ? name.substring(0, 200) : name, kcal, protein, fat, carbs));
            }
            if (foods.size() == limit) {
                break;
            }
        }
        return foods;
    }

    private static BigDecimal number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            return null;
        }
        BigDecimal v = value.decimalValue().setScale(1, RoundingMode.HALF_UP);
        return v.signum() < 0 ? null : v;
    }
}
