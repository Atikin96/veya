package com.veya.integration.food;

import com.veya.integration.food.FoodProvider.ProviderFood;
import com.veya.integration.food.FoodProvider.ProviderTimeoutException;
import com.veya.integration.food.FoodProvider.ProviderUnavailableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("OpenFoodFactsProvider against a stub server: normalization, invalid products, failure and timeout")
class OpenFoodFactsProviderTest {

    private static StubFoodProvider stub;

    @BeforeAll
    static void start() {
        stub = new StubFoodProvider();
    }

    @AfterAll
    static void stop() {
        stub.close();
    }

    private static OpenFoodFactsProvider provider() {
        return new OpenFoodFactsProvider(true, stub.url(), Duration.ofMillis(500));
    }

    @Test
    @DisplayName("Products are normalized per 100 g (one decimal); incomplete or implausible products are skipped")
    void normalizes() {
        stub.mode(StubFoodProvider.Mode.OK);
        List<ProviderFood> foods = provider().search("quinoa", 10);
        assertThat(foods).singleElement().satisfies(f -> {
            assertThat(f.externalRef()).isEqualTo("4000000000001");
            assertThat(f.name()).isEqualTo("Quinoa, cooked");
            assertThat(f.kcalPer100g()).isEqualByComparingTo("120.0");
            assertThat(f.fatPer100g()).isEqualByComparingTo("1.9");
        });
        assertThat(provider().enabled()).isTrue();
        assertThat(provider().attribution()).contains("Open Food Facts", "ODbL");
    }

    @Test
    @DisplayName("HTTP 500 -> ProviderUnavailableException; slow answer -> ProviderTimeoutException; unreachable -> unavailable")
    void failures() {
        stub.mode(StubFoodProvider.Mode.ERROR);
        assertThatThrownBy(() -> provider().search("quinoa", 10)).isInstanceOf(ProviderUnavailableException.class);
        stub.mode(StubFoodProvider.Mode.SLOW);
        assertThatThrownBy(() -> provider().search("quinoa", 10)).isInstanceOf(ProviderTimeoutException.class);
        assertThatThrownBy(() -> new OpenFoodFactsProvider(true, "http://127.0.0.1:1", Duration.ofMillis(300)).search("x", 1))
                .isInstanceOf(ProviderUnavailableException.class);
        stub.mode(StubFoodProvider.Mode.OK);
    }
}
