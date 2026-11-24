package com.veya.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Builds RFC 9457 Problem Details in the VEYA format ({@code type}, {@code title}, {@code status}, {@code code}, ...). */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, HttpStatusCode status, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(code.type());
        problem.setTitle(code.title());
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            problem.setProperty("traceId", requestId); // NFR-18: correlates the error with the server log
        }
        return problem;
    }

    public static ProblemDetail of(ErrorCode code, String detail, String instance) {
        return of(code, code.status(), detail, instance);
    }

    public static ProblemDetail withErrors(ProblemDetail problem, List<FieldViolation> errors) {
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    public static ProblemDetail withProperties(ProblemDetail problem, Map<String, Object> properties) {
        properties.forEach(problem::setProperty);
        return problem;
    }

    public static ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** Request path without the query string - never echoes query values (they may be personal). */
    public static String instance(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
