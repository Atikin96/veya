package com.veya.nutrition.application;

import com.veya.integration.food.FoodProvider;
import com.veya.integration.food.FoodProvider.ProviderFood;
import com.veya.integration.food.FoodProvider.ProviderTimeoutException;
import com.veya.integration.food.FoodProvider.ProviderUnavailableException;
import com.veya.nutrition.infrastructure.CatalogRepository;
import com.veya.nutrition.infrastructure.CatalogRepository.FoodRow;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * US-05 / US-31 food search with the provider fallback of the contract (AQ-7 PROPOSED, R1):
 * <ul>
 *     <li>provider answers: its normalized products are cached in the local catalogue (source PROVIDER, so they get a
 *     stable foodId) and returned with the local matches, {@code providerStatus = OK};</li>
 *     <li>provider fails or times out but local matches exist: 200 with {@code providerStatus = UNAVAILABLE};</li>
 *     <li>no local matches: 502 PROVIDER_UNAVAILABLE / 504 PROVIDER_TIMEOUT with {@code manualEntryAllowed = true};</li>
 *     <li>no provider configured: local catalogue only, {@code providerStatus = UNAVAILABLE}.</li>
 * </ul>
 */
@Service
public class FoodSearchService {

    private static final Logger log = LoggerFactory.getLogger(FoodSearchService.class);

    public record Result(String providerStatus, List<FoodRow> items, String attribution) {
    }

    private final CatalogRepository catalog;
    private final FoodProvider provider;

    public FoodSearchService(CatalogRepository catalog, FoodProvider provider) {
        this.catalog = catalog;
        this.provider = provider;
    }

    @Transactional
    public Result search(String text, int limit) {
        List<FoodRow> local = catalog.searchFoods(text, limit);
        if (!provider.enabled()) {
            return new Result("UNAVAILABLE", local, provider.attribution());
        }
        try {
            List<ProviderFood> found = provider.search(text, limit);
            Map<Long, FoodRow> merged = new LinkedHashMap<>();
            local.forEach(f -> merged.put(f.foodId(), f));
            for (ProviderFood food : found) {
                FoodRow cached = catalog.cacheProviderFood(food);
                merged.putIfAbsent(cached.foodId(), cached);
            }
            return new Result("OK", merged.values().stream().limit(limit).toList(), provider.attribution());
        } catch (ProviderTimeoutException ex) {
            log.warn("Food provider timed out; local results={}", local.size());
            if (local.isEmpty()) {
                throw providerError(ErrorCode.PROVIDER_TIMEOUT, "The food provider did not answer in time. Retry or enter the food manually.");
            }
        } catch (ProviderUnavailableException ex) {
            log.warn("Food provider unavailable ({}); local results={}", ex.getMessage(), local.size());
            if (local.isEmpty()) {
                throw providerError(ErrorCode.PROVIDER_UNAVAILABLE, "The food provider is not available. You can enter the food manually.");
            }
        }
        return new Result("UNAVAILABLE", local, provider.attribution());
    }

    private static ApiException providerError(ErrorCode code, String detail) {
        return new ApiException(code, detail, List.of(), Map.of("manualEntryAllowed", true));
    }
}
