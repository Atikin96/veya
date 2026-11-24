package com.veya.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Security settings. The JWT signing secret is never stored in the repository: locally it comes from the git-ignored
 * {@code infra/.env} ({@code VEYA_JWT_SECRET}, created by {@code infra/generate-env.sh}), elsewhere from the
 * environment; tests generate a random one per run.
 *
 * @param jwtSecret      Base64-encoded HMAC-SHA256 key, at least 32 bytes
 * @param jwtTtl         access token lifetime (AQ-5 PROPOSED: 60 minutes, no refresh token)
 * @param jwtIssuer      {@code iss} claim written and required
 * @param bcryptStrength BCrypt cost factor (database CHECK accepts 04-31)
 * @param corsAllowedOrigins origins of the React client allowed to call the API
 */
@ConfigurationProperties(prefix = "veya.security")
public record SecurityProperties(String jwtSecret, Duration jwtTtl, String jwtIssuer, int bcryptStrength,
                                 List<String> corsAllowedOrigins) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("veya.security.jwt-secret is not set. Set VEYA_JWT_SECRET "
                    + "(run 'sh infra/generate-env.sh' to add a random one to infra/.env).");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(jwtSecret.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("VEYA_JWT_SECRET must be Base64-encoded");
        }
        if (key.length < 32) {
            throw new IllegalStateException("VEYA_JWT_SECRET must encode at least 32 bytes (256 bits)");
        }
        if (jwtTtl == null || jwtTtl.isNegative() || jwtTtl.isZero()) {
            throw new IllegalStateException("veya.security.jwt-ttl must be positive");
        }
        if (bcryptStrength < 4 || bcryptStrength > 31) {
            throw new IllegalStateException("veya.security.bcrypt-strength must be between 4 and 31");
        }
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }

    public byte[] jwtKey() {
        return Base64.getDecoder().decode(jwtSecret.trim());
    }
}
