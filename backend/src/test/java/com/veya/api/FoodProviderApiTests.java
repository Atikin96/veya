package com.veya.api;

import com.veya.integration.food.StubFoodProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US-05 / US-31 over real HTTP with the provider enabled against a local stub (controlled test double). */
class FoodProviderApiTests extends ApiTestSupport {

    private static final StubFoodProvider STUB = new StubFoodProvider();

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("veya.food-provider.enabled", () -> "true");
        registry.add("veya.food-provider.base-url", STUB::url);
        registry.add("veya.food-provider.timeout", () -> "PT0.5S");
    }

    @AfterAll
    static void stop() {
        STUB.close();
    }

    @Test
    @DisplayName("Provider OK: products are normalized, cached with a foodId and attribution; then usable in the diary")
    void providerResultsAreCachedAndUsable() {
        TestUser user = newUser("provider");
        STUB.mode(StubFoodProvider.Mode.OK);
        JsonNode result = expect(200, "GET", "/api/v1/foods?q=quinoa", user.token(), null).json();
        assertThat(result.get("providerStatus").asString()).isEqualTo("OK");
        JsonNode quinoa = result.get("items").get(0);
        assertThat(quinoa.get("source").asString()).isEqualTo("PROVIDER");
        assertThat(quinoa.get("attribution").asString()).contains("Open Food Facts");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.food_item WHERE source = 'PROVIDER' AND external_ref = '4000000000001'",
                Integer.class)).isEqualTo(1);
        Map<String, Object> entry = NutritionApiTests.manual("2026-11-16", "LUNCH", "Quinoa, cooked", 150, 180);
        entry.put("inputSource", "CATALOG");
        entry.put("foodId", quinoa.get("foodId").asLong());
        expect(201, "POST", "/api/v1/food-entries", user.token(), entry);
        // a second search updates the cached row instead of duplicating it
        expect(200, "GET", "/api/v1/foods?q=quinoa", user.token(), null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.food_item WHERE external_ref = '4000000000001'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Provider down: local matches -> 200 UNAVAILABLE; no matches -> 502 / 504 with manualEntryAllowed")
    void fallback() {
        TestUser user = newUser("fallback");
        STUB.mode(StubFoodProvider.Mode.ERROR);
        assertThat(expect(200, "GET", "/api/v1/foods?q=chick", user.token(), null).json().get("providerStatus").asString())
                .isEqualTo("UNAVAILABLE");
        JsonNode unavailable = expect(502, "GET", "/api/v1/foods?q=zzzz-unknown", user.token(), null).json();
        assertThat(unavailable.get("code").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(unavailable.get("manualEntryAllowed").asBoolean()).isTrue();
        STUB.mode(StubFoodProvider.Mode.SLOW);
        assertThat(expect(504, "GET", "/api/v1/foods?q=zzzz-unknown", user.token(), null).json().get("code").asString())
                .isEqualTo("PROVIDER_TIMEOUT");
        STUB.mode(StubFoodProvider.Mode.OK);
    }
}
