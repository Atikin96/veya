package com.veya.shared.web;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Locale;

/**
 * Stable application error codes (NFR-14), exactly the {@code ErrorCode} enum of docs/api/openapi.yaml. The problem
 * {@code type} URI is derived from the code, e.g. {@code VALIDATION_FAILED -> https://veya.example/problems/validation-failed}.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request is not valid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST, "Confirmation required"),
    INVALID_EDIT(HttpStatus.BAD_REQUEST, "Invalid edit"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profile not found"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Plan not found"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "E-mail already registered"),
    CONSENT_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Consent already active"),
    CONSENT_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "Consent already withdrawn"),
    SAVED_MEAL_NAME_TAKEN(HttpStatus.CONFLICT, "Name already used"),
    PLAN_ALREADY_EXISTS(HttpStatus.CONFLICT, "Plan already exists"),
    STALE_PLAN_VERSION(HttpStatus.CONFLICT, "Plan version is outdated"),
    PROPOSAL_ALREADY_DECIDED(HttpStatus.CONFLICT, "Proposal already decided"),
    RECOVERY_NOT_REQUIRED(HttpStatus.CONFLICT, "Recovery not required"),
    ITEM_FROZEN(HttpStatus.CONFLICT, "Item cannot be changed"),
    STATUS_ALREADY_RECORDED(HttpStatus.CONFLICT, "Status already recorded"),
    IMMUTABLE_HISTORY(HttpStatus.CONFLICT, "History cannot be changed"),
    HARD_CONSTRAINT_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "Hard constraint violated"),
    NO_FEASIBLE_ALTERNATIVE(HttpStatus.UNPROCESSABLE_CONTENT, "No feasible alternative"),
    NO_FEASIBLE_MEAL_PLAN(HttpStatus.UNPROCESSABLE_CONTENT, "No feasible meal plan"),
    RECOVERY_NOT_FEASIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "Recovery not feasible"),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Food provider unavailable"),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Food provider timed out"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private static final String TYPE_BASE = "https://veya.example/problems/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
