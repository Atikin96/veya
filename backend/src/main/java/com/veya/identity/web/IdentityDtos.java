package com.veya.identity.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies of the identity operations (schemas of docs/api/openapi.yaml with the same names). */
public final class IdentityDtos {

    /** Same rule as the database CHECK app_user_email_format. */
    static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 254) @Pattern(regexp = EMAIL, message = "must be a valid e-mail address") String email,
            @NotNull @Size(min = 8, max = 72) String password,
            @NotNull @AssertTrue(message = "must be true: registration is limited to adults (Q8, Q13)") Boolean adultConfirmed,
            @NotNull @Size(min = 1) List<@NotNull @Positive Integer> consentDocumentIds) {

        public RegisterRequest {
            email = email == null ? null : email.trim();
        }

        @Override
        public String toString() {
            return "RegisterRequest[password=***]"; // never log credentials or e-mail (LEG-5)
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotNull @Size(min = 1, max = 72) String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }

        @Override
        public String toString() {
            return "LoginRequest[password=***]";
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record Account(UUID userId, String email, Instant adultConfirmedAt, Instant createdAt,
                          boolean hasProfile) {
    }

    public record ConsentDocument(int consentDocumentId, String consentType, String version, LocalDate publishedOn,
                                  String summary) {
    }

    public record UserConsent(UUID consentId, int consentDocumentId, String consentType, String version,
                              Instant grantedAt, Instant withdrawnAt) {
    }

    public record GrantConsentRequest(@NotNull @Positive Integer consentDocumentId) {
    }
}
