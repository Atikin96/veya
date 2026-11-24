package com.veya.nutrition.application;

import com.veya.integration.food.FoodProvider;
import com.veya.integration.food.FoodProvider.ProviderFood;
import com.veya.integration.food.FoodProvider.ProviderTimeoutException;
import com.veya.integration.food.FoodProvider.ProviderUnavailableException;
import com.veya.nutrition.infrastructure.CatalogRepository;
import com.veya.nutrition.infrastructure.CatalogRepository.FoodRow;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("FoodSearchService: provider fallback rules (US-05, US-31, AQ-7)")
class FoodSearchServiceTest {

    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final FoodProvider provider = mock(FoodProvider.class);
    private final FoodSearchService service = new FoodSearchService(catalog, provider);
    private final FoodRow local = new FoodRow(4, "Chicken breast, cooked", "LOCAL", new BigDecimal("165.0"), BigDecimal.ONE,
            BigDecimal.ONE, BigDecimal.ZERO);
    private final ProviderFood quinoa = new ProviderFood("400", "Quinoa", new BigDecimal("120"), BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.TEN);
    private final FoodRow cached = new FoodRow(77, "Quinoa", "PROVIDER", new BigDecimal("120"), BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.TEN);

    @Test
    @DisplayName("No provider configured: local catalogue only, providerStatus UNAVAILABLE, the provider is never called")
    void disabled() {
        when(catalog.searchFoods("chick", 20)).thenReturn(List.of(local));
        FoodSearchService.Result r = service.search("chick", 20);
        assertThat(r.providerStatus()).isEqualTo("UNAVAILABLE");
        assertThat(r.items()).containsExactly(local);
        verify(provider, never()).search(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Provider answers: products are cached and merged after the local matches, providerStatus OK")
    void providerOk() {
        when(provider.enabled()).thenReturn(true);
        when(catalog.searchFoods("qu", 20)).thenReturn(List.of(local));
        when(provider.search("qu", 20)).thenReturn(List.of(quinoa));
        when(catalog.cacheProviderFood(quinoa)).thenReturn(cached);
        FoodSearchService.Result r = service.search("qu", 20);
        assertThat(r.providerStatus()).isEqualTo("OK");
        assertThat(r.items()).containsExactly(local, cached);
    }

    @Test
    @DisplayName("Provider fails with local matches -> 200 UNAVAILABLE; without -> 502 / 504 with manualEntryAllowed")
    void fallback() {
        when(provider.enabled()).thenReturn(true);
        when(catalog.searchFoods("chick", 20)).thenReturn(List.of(local));
        when(provider.search(any(), org.mockito.ArgumentMatchers.anyInt())).thenThrow(new ProviderUnavailableException("down", null));
        assertThat(service.search("chick", 20).providerStatus()).isEqualTo("UNAVAILABLE");

        when(catalog.searchFoods("quinoa", 20)).thenReturn(List.of());
        assertThatThrownBy(() -> service.search("quinoa", 20)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.PROVIDER_UNAVAILABLE);
            assertThat(e.properties()).containsEntry("manualEntryAllowed", true);
        });

        doThrow(new ProviderTimeoutException("slow", null)).when(provider).search(any(), org.mockito.ArgumentMatchers.anyInt());
        assertThatThrownBy(() -> service.search("quinoa", 20)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROVIDER_TIMEOUT));
        assertThat(service.search("chick", 20).providerStatus()).isEqualTo("UNAVAILABLE");
    }
}
