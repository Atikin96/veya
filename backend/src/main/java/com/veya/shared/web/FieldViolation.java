package com.veya.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One entry of {@code Problem.errors} (schema {@code FieldError}); {@code rejectedValue} is never set for passwords. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldViolation(String field, String message, Object rejectedValue) {

    public static FieldViolation of(String field, String message) {
        return new FieldViolation(field, message, null);
    }
}
