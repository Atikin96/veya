package com.veya.integration.food;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port to an external food database (US-05, US-31, integration module). Implementations normalize products to
 * kcal and macros per 100 g and must fail fast: {@link ProviderTimeoutException} when the provider does not answer in
 * time, {@link ProviderUnavailableException} for any other failure. Callers fall back to the local catalogue (R1).
 */
public interface FoodProvider {

    /** A normalized provider product. {@code externalRef} is the provider's stable id (e.g. barcode). */
    record ProviderFood(String externalRef, String name, BigDecimal kcalPer100g, BigDecimal proteinPer100g,
                        BigDecimal fatPer100g, BigDecimal carbsPer100g) {
    }

    boolean enabled();

    /** Attribution text required by the provider's licence (LEG-7). */
    String attribution();

    List<ProviderFood> search(String text, int limit);

    class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class ProviderTimeoutException extends RuntimeException {
        public ProviderTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
