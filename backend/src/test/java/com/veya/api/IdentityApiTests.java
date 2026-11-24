package com.veya.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tools.jackson.databind.JsonNode;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Identity API over real HTTP: US-01, US-04, US-30, LEG-2/LEG-4 (Q6, Q7), Q8/Q13 adult confirmation. */
class IdentityApiTests extends ApiTestSupport {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("US-01: registration creates the account with a bcrypt hash, the adult confirmation and both consents")
    void registerCreatesAccount() {
        String email = uniqueEmail("anna");
        Response response = expect(201, "POST", "/api/v1/auth/register", null, registration(email));

        JsonNode account = response.json();
        assertThat(account.get("email").asString()).isEqualTo(email);
        assertThat(account.get("hasProfile").asBoolean()).isFalse();
        assertThat(response.header("Location")).isEqualTo("/api/v1/me");
        assertThat(response.body()).doesNotContain("password", PASSWORD);

        UUID id = UUID.fromString(account.get("userId").asString());
        Map<String, Object> row = jdbc.queryForMap("SELECT password_hash, adult_confirmed_at FROM veya.app_user WHERE id = ?", id);
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(row.get("adult_confirmed_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.user_consent WHERE user_id = ? AND withdrawn_at IS NULL",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Q8/Q13: registration without the adult confirmation is rejected with a field error and nothing is stored")
    void registerRequiresAdultConfirmation() {
        String email = uniqueEmail("teen");
        Map<String, Object> body = new HashMap<>(registration(email));
        body.put("adultConfirmed", false);

        Response response = expect(400, "POST", "/api/v1/auth/register", null, body);

        assertProblem(response, 400, "VALIDATION_FAILED");
        assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("adultConfirmed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.app_user WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    @DisplayName("Registration validates e-mail, password length (never echoed), unknown fields and consent documents")
    void registerValidation() {
        Map<String, Object> shortPassword = new HashMap<>(registration(uniqueEmail("x")));
        shortPassword.put("password", "short");
        Response r1 = expect(400, "POST", "/api/v1/auth/register", null, shortPassword);
        assertThat(r1.json().get("errors").get(0).get("field").asString()).isEqualTo("password");
        assertThat(r1.json().get("errors").get(0).has("rejectedValue")).isFalse();
        assertThat(r1.body()).doesNotContain("short\"");

        Map<String, Object> badEmail = new HashMap<>(registration("not-an-email"));
        assertProblem(expect(400, "POST", "/api/v1/auth/register", null, badEmail), 400, "VALIDATION_FAILED");

        Map<String, Object> onlyPrivacy = new HashMap<>(registration(uniqueEmail("x")));
        onlyPrivacy.put("consentDocumentIds", List.of(1));
        assertThat(expect(400, "POST", "/api/v1/auth/register", null, onlyPrivacy).body()).contains("consentDocumentIds");

        Map<String, Object> unknownDoc = new HashMap<>(registration(uniqueEmail("x")));
        unknownDoc.put("consentDocumentIds", List.of(1, 2, 999));
        assertThat(expect(400, "POST", "/api/v1/auth/register", null, unknownDoc).body()).contains("consentDocumentIds[2]");

        Map<String, Object> unknownField = new HashMap<>(registration(uniqueEmail("x")));
        unknownField.put("birthDate", "2000-01-01");
        assertThat(expect(400, "POST", "/api/v1/auth/register", null, unknownField).body()).contains("birthDate");

        Response malformed = expect(400, "POST", "/api/v1/auth/register", null, "{not json");
        assertProblem(malformed, 400, "MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("Registration with an already registered e-mail (any case) returns 409 EMAIL_ALREADY_REGISTERED (AQ-6 proposed)")
    void registerDuplicateEmail() {
        String email = uniqueEmail("dup");
        expect(201, "POST", "/api/v1/auth/register", null, registration(email));

        Response response = expect(409, "POST", "/api/v1/auth/register", null, registration(email.toUpperCase()));

        assertProblem(response, 409, "EMAIL_ALREADY_REGISTERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.app_user WHERE lower(email) = lower(?)", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("US-01: sign-in returns a bearer JWT; wrong password and unknown e-mail get the identical 401")
    void login() {
        TestUser user = newUser("login");
        Response ok = expect(200, "POST", "/api/v1/auth/login", null, Map.of("email", user.email(), "password", PASSWORD));
        assertThat(ok.json().get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(ok.json().get("expiresIn").asLong()).isEqualTo(3600);
        assertThat(ok.json().get("accessToken").asString().split("\\.")).hasSize(3);

        Response wrongPassword = expect(401, "POST", "/api/v1/auth/login", null,
                Map.of("email", user.email(), "password", "definitely-wrong"));
        Response unknownEmail = expect(401, "POST", "/api/v1/auth/login", null,
                Map.of("email", uniqueEmail("nobody"), "password", "definitely-wrong"));
        assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
        assertThat(unknownEmail.json().get("detail")).isEqualTo(wrongPassword.json().get("detail"));
        assertThat(unknownEmail.json().get("code")).isEqualTo(wrongPassword.json().get("code"));

        assertProblem(expect(400, "POST", "/api/v1/auth/login", null, Map.of("email", user.email())), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("US-30: the current account needs a valid token; missing, malformed, foreign-key and expired tokens get 401")
    void meRequiresValidToken() {
        TestUser user = newUser("me");
        Response me = expect(200, "GET", "/api/v1/me", user.token(), null);
        assertThat(me.json().get("userId").asString()).isEqualTo(user.id().toString());
        assertThat(me.body()).doesNotContain("password");

        Response missing = expect(401, "GET", "/api/v1/me", null, null);
        assertProblem(missing, 401, "AUTHENTICATION_REQUIRED");
        assertThat(missing.header("WWW-Authenticate")).startsWith("Bearer");
        assertThat(missing.body()).doesNotContain(user.email());

        assertProblem(expect(401, "GET", "/api/v1/me", "not-a-jwt", null), 401, "AUTHENTICATION_REQUIRED");

        byte[] otherKey = new byte[32];
        new java.security.SecureRandom().nextBytes(otherKey);
        JwtEncoder foreign = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(
                new SecretKeySpec(otherKey, "HmacSHA256")));
        assertProblem(expect(401, "GET", "/api/v1/me", token(foreign, user.id(), Instant.now().plusSeconds(600)), null),
                401, "AUTHENTICATION_REQUIRED");

        assertProblem(expect(401, "GET", "/api/v1/me", token(jwtEncoder, user.id(), Instant.now().minusSeconds(600)), null),
                401, "AUTHENTICATION_REQUIRED");
        assertProblem(expect(401, "GET", "/api/v1/me", token(jwtEncoder, UUID.randomUUID(), Instant.now().plusSeconds(600)), null),
                401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("LEG-2/Q7: consent documents are public; consents can be listed, re-granted only when not active, withdrawn once")
    void consents() {
        Response docs = expect(200, "GET", "/api/v1/consent-documents", null, null);
        assertThat(docs.json().size()).isEqualTo(2);

        TestUser user = newUser("consent");
        Response mine = expect(200, "GET", "/api/v1/me/consents", user.token(), null);
        assertThat(mine.json().size()).isEqualTo(2);
        String healthConsent = null;
        for (JsonNode consent : mine.json()) {
            if (consent.get("consentType").asString().equals("HEALTH_DATA_CONSENT")) {
                healthConsent = consent.get("consentId").asString();
            }
        }

        assertProblem(expect(409, "POST", "/api/v1/me/consents", user.token(), Map.of("consentDocumentId", 2)),
                409, "CONSENT_ALREADY_ACTIVE");
        assertProblem(expect(404, "POST", "/api/v1/me/consents", user.token(), Map.of("consentDocumentId", 999)),
                404, "RESOURCE_NOT_FOUND");

        String withdrawal = "/api/v1/me/consents/{consentId}/withdrawal";
        TestUser other = newUser("intruder");
        assertProblem(expect(403, "POST", withdrawal, withdrawal.replace("{consentId}", healthConsent), other.token(), null),
                403, "ACCESS_DENIED");
        assertProblem(expect(404, "POST", withdrawal, withdrawal.replace("{consentId}", UUID.randomUUID().toString()),
                user.token(), null), 404, "RESOURCE_NOT_FOUND");

        Response withdrawn = expect(200, "POST", withdrawal, withdrawal.replace("{consentId}", healthConsent), user.token(), null);
        assertThat(withdrawn.json().get("withdrawnAt").isNull()).isFalse();
        assertProblem(expect(409, "POST", withdrawal, withdrawal.replace("{consentId}", healthConsent), user.token(), null),
                409, "CONSENT_ALREADY_WITHDRAWN");

        Response regranted = expect(201, "POST", "/api/v1/me/consents", user.token(), Map.of("consentDocumentId", 2));
        assertThat(regranted.json().get("withdrawnAt").isNull()).isTrue();
        assertProblem(expect(401, "GET", "/api/v1/me/consents", null, null), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("LEG-4/Q6: export contains only the caller's records and no password hash")
    void export() {
        TestUser user = newUser("export");
        newUser("someone-else");
        Response export = expect(200, "GET", "/api/v1/me/export", user.token(), null);
        assertThat(export.header("Content-Disposition")).contains("attachment").contains("veya-export.json");
        assertThat(export.json().get("account").get("email").asString()).isEqualTo(user.email());
        assertThat(export.body()).doesNotContain("password", "someone-else");
        assertProblem(expect(401, "GET", "/api/v1/me/export", null, null), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("US-04: deletion needs confirm=true, erases the personal records and invalidates the previous token")
    void deleteAccount() {
        TestUser user = newUser("delete");
        assertProblem(expect(400, "DELETE", "/api/v1/me", user.token(), null), 400, "CONFIRMATION_REQUIRED");
        assertProblem(expect(400, "DELETE", "/api/v1/me", "/api/v1/me?confirm=false", user.token(), null), 400, "CONFIRMATION_REQUIRED");

        expect(204, "DELETE", "/api/v1/me", "/api/v1/me?confirm=true", user.token(), null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.app_user WHERE id = ?", Integer.class, user.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veya.user_consent WHERE user_id = ?", Integer.class, user.id())).isZero();
        assertProblem(expect(401, "GET", "/api/v1/me", user.token(), null), 401, "AUTHENTICATION_REQUIRED");
        assertProblem(expect(401, "DELETE", "/api/v1/me", "/api/v1/me?confirm=true", user.token(), null), 401, "AUTHENTICATION_REQUIRED");
        expect(401, "POST", "/api/v1/auth/login", null, Map.of("email", user.email(), "password", PASSWORD));
    }

    private static String token(JwtEncoder encoder, UUID subject, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("veya-backend").subject(subject.toString())
                .issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
