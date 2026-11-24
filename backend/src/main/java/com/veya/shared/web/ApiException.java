package com.veya.shared.web;

import java.util.List;
import java.util.Map;

/**
 * An expected, documented API error. It is rendered as RFC 9457 Problem Details with the stable {@link ErrorCode}
 * by {@link GlobalExceptionHandler}. The detail text must not contain another user's data or credentials.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldViolation> errors;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of(), Map.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldViolation> errors, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.errors = List.copyOf(errors);
        this.properties = Map.copyOf(properties);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                List.of(FieldViolation.of(field, message)), Map.of());
    }

    public static ApiException validation(List<FieldViolation> errors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", errors, Map.of());
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "The requested resource does not exist.");
    }

    /** Another user's existing resource (US-30, NFR-08; contract decision AQ-2: 403 for existing, 404 for unknown ids). */
    public static ApiException accessDenied() {
        return new ApiException(ErrorCode.ACCESS_DENIED, "You cannot access this resource.");
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldViolation> errors() {
        return errors;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
