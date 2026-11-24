package com.veya.identity.web;

import com.veya.identity.api.CurrentUser;
import com.veya.identity.application.AccountService;
import com.veya.identity.application.ConsentService;
import com.veya.identity.domain.AppUser;
import com.veya.identity.domain.UserConsent;
import com.veya.identity.infrastructure.security.TokenService.IssuedToken;
import com.veya.identity.web.IdentityDtos.Account;
import com.veya.identity.web.IdentityDtos.ConsentDocument;
import com.veya.identity.web.IdentityDtos.GrantConsentRequest;
import com.veya.identity.web.IdentityDtos.LoginRequest;
import com.veya.identity.web.IdentityDtos.RegisterRequest;
import com.veya.identity.web.IdentityDtos.TokenResponse;
import com.veya.profile.api.ProfileLookup;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Identity operations of docs/api/openapi.yaml (tag identity). */
@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final AccountService accounts;
    private final ConsentService consents;
    private final ProfileLookup profiles;

    public IdentityController(AccountService accounts, ConsentService consents, ProfileLookup profiles) {
        this.accounts = accounts;
        this.consents = consents;
        this.profiles = profiles;
    }

    /** operationId register (US-01). */
    @PostMapping("/auth/register")
    public ResponseEntity<Account> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = accounts.register(request.email(), request.password(), request.consentDocumentIds());
        return ResponseEntity.created(URI.create("/api/v1/me")).body(account(user));
    }

    /** operationId login (US-01, US-30). */
    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken token = accounts.login(request.email(), request.password());
        return new TokenResponse(token.accessToken(), "Bearer", token.expiresInSeconds());
    }

    /** operationId getMe (US-01, US-30). */
    @GetMapping("/me")
    public Account me(CurrentUser user) {
        return account(accounts.account(user.id()));
    }

    /** operationId deleteMyAccount (US-04): requires the explicit confirmation {@code confirm=true}. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(CurrentUser user, @RequestParam(name = "confirm", required = false) String confirm) {
        if (!"true".equals(confirm)) {
            throw new ApiException(ErrorCode.CONFIRMATION_REQUIRED, "Account deletion requires confirm=true.");
        }
        accounts.delete(user.id());
        return ResponseEntity.noContent().build();
    }

    /** operationId exportMyData (LEG-4, Q6). */
    @GetMapping(path = "/me/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportMyData(CurrentUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("veya-export.json").build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(accounts.export(user.id()));
    }

    /** operationId listConsentDocuments (LEG-2, Q7) - public. */
    @GetMapping("/consent-documents")
    public List<ConsentDocument> consentDocuments() {
        return consents.currentDocuments().stream()
                .map(d -> new ConsentDocument(d.getId(), d.getConsentType(), d.getVersion(), d.getPublishedOn(), d.getSummary()))
                .toList();
    }

    /** operationId listMyConsents. */
    @GetMapping("/me/consents")
    public List<IdentityDtos.UserConsent> myConsents(CurrentUser user) {
        return consents.myConsents(user.id()).stream().map(IdentityController::consent).toList();
    }

    /** operationId grantConsent. */
    @PostMapping("/me/consents")
    public ResponseEntity<IdentityDtos.UserConsent> grantConsent(CurrentUser user, @Valid @RequestBody GrantConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consent(consents.grant(user.id(), request.consentDocumentId())));
    }

    /** operationId withdrawConsent. */
    @PostMapping("/me/consents/{consentId}/withdrawal")
    public IdentityDtos.UserConsent withdrawConsent(CurrentUser user, @PathVariable("consentId") UUID consentId) {
        return consent(consents.withdraw(user.id(), consentId));
    }

    private Account account(AppUser user) {
        return new Account(user.getId(), user.getEmail(), user.getAdultConfirmedAt(), user.getCreatedAt(),
                profiles.hasProfile(user.getId()));
    }

    private static IdentityDtos.UserConsent consent(UserConsent c) {
        return new IdentityDtos.UserConsent(c.getId(), c.getDocument().getId(), c.getDocument().getConsentType(),
                c.getDocument().getVersion(), c.getGrantedAt(), c.getWithdrawnAt());
    }
}
