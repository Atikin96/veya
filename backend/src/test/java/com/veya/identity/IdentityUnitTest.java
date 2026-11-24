package com.veya.identity;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.veya.identity.domain.ConsentDocument;
import com.veya.identity.domain.UserConsent;
import com.veya.identity.infrastructure.security.SecurityProperties;
import com.veya.identity.infrastructure.security.TokenService;
import com.veya.shared.web.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Identity: security settings, token issuing, registration rules, consent withdrawal")
class IdentityUnitTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static SecurityProperties props(String secret, Duration ttl, int strength) {
        return new SecurityProperties(secret, ttl, "veya-backend", strength, List.of("http://localhost:5173"));
    }

    @Test
    @DisplayName("The application refuses to start without a proper JWT secret, TTL or bcrypt cost")
    void securityPropertiesValidation() {
        assertThatThrownBy(() -> props(null, Duration.ofHours(1), 12)).hasMessageContaining("VEYA_JWT_SECRET");
        assertThatThrownBy(() -> props(" ", Duration.ofHours(1), 12)).hasMessageContaining("not set");
        assertThatThrownBy(() -> props("not base64!", Duration.ofHours(1), 12)).hasMessageContaining("Base64");
        assertThatThrownBy(() -> props(Base64.getEncoder().encodeToString(new byte[31]), Duration.ofHours(1), 12))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> props(KEY, Duration.ZERO, 12)).hasMessageContaining("positive");
        assertThatThrownBy(() -> props(KEY, Duration.ofHours(1), 3)).hasMessageContaining("bcrypt");
        assertThatThrownBy(() -> props(KEY, Duration.ofHours(1), 32)).hasMessageContaining("bcrypt");
        assertThat(props(KEY, Duration.ofHours(1), 4).jwtKey()).hasSize(32);
        assertThat(new SecurityProperties(KEY, Duration.ofHours(1), "i", 10, null).corsAllowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("Issued tokens carry only sub/iss/iat/exp/jti, expire after the TTL and verify with the same key")
    void tokens() {
        SecurityProperties properties = props(KEY, Duration.ofMinutes(60), 12);
        SecretKeySpec key = new SecretKeySpec(properties.jwtKey(), "HmacSHA256");
        TokenService service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties);
        UUID user = UUID.randomUUID();
        TokenService.IssuedToken issued = service.issue(user);
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);

        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(issued.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("veya-backend");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iss", "iat", "exp", "jti");
        assertThat(service.issue(user).accessToken()).isNotEqualTo(issued.accessToken()); // unique jti
    }

    @Test
    @DisplayName("Registration rules: <= 72 bytes (multi-byte counted), no duplicates, only and all current documents")
    void registrationRules() throws Exception {
        Method validate = Class.forName("com.veya.identity.application.AccountService")
                .getDeclaredMethod("validateRegistration", String.class, List.class, Set.class);
        validate.setAccessible(true);
        assertThat(errors(validate, "a".repeat(72), List.of(1, 2))).isEmpty();
        assertThat(errors(validate, "a".repeat(73), List.of(1, 2))).extracting(FieldViolation::field).containsExactly("password");
        assertThat(errors(validate, "ä".repeat(37), List.of(1, 2))).extracting(FieldViolation::field).containsExactly("password");
        assertThat(errors(validate, "password", List.of(1, 1, 2))).extracting(FieldViolation::field).contains("consentDocumentIds");
        assertThat(errors(validate, "password", List.of(1, 2, 9))).extracting(FieldViolation::field).contains("consentDocumentIds[2]");
        assertThat(errors(validate, "password", List.of(1))).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("all current consent documents [1, 2]"));
    }

    @SuppressWarnings("unchecked")
    private static List<FieldViolation> errors(Method validate, String password, List<Integer> docs) throws Exception {
        return (List<FieldViolation>) validate.invoke(null, password, docs, Set.of(1, 2));
    }

    @Test
    @DisplayName("A consent is withdrawn once, never before it was granted")
    void consentWithdrawal() throws Exception {
        Constructor<ConsentDocument> c = ConsentDocument.class.getDeclaredConstructor();
        c.setAccessible(true);
        UserConsent consent = new UserConsent(UUID.randomUUID(), c.newInstance());
        assertThat(consent.isActive()).isTrue();
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        consent.withdraw(now);
        assertThat(consent.isActive()).isFalse();
        assertThat(consent.getWithdrawnAt()).isEqualTo(now);
        assertThatThrownBy(() -> consent.withdraw(now)).isInstanceOf(IllegalStateException.class);
    }
}
