package com.veya.shared.web;

import com.veya.shared.persistence.DatabaseErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders every error as RFC 9457 Problem Details with a stable {@code code} (NFR-14). Never logs request bodies,
 * passwords, tokens or database values (LEG-5, NFR-08): unexpected errors are logged by type and SQL state only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_FIELDS = "One or more fields are invalid.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ex.code(), ex.getMessage(), ProblemDetails.instance(request));
        ProblemDetails.withErrors(problem, ex.errors());
        ProblemDetails.withProperties(problem, ex.properties());
        return ProblemDetails.response(problem);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Object> handleDatabase(DataAccessException ex, HttpServletRequest request) {
        ApiException mapped = DatabaseErrors.toApiException(ex);
        if (mapped == null) {
            log.error("Unexpected database error {} (SQL state {}, root cause {}) {}", ex.getClass().getSimpleName(),
                    DatabaseErrors.sqlState(ex), rootCause(ex).getClass().getName(), DatabaseErrors.loggableDetail(ex));
            return ProblemDetails.response(ProblemDetails.of(ErrorCode.INTERNAL_ERROR,
                    "An unexpected error occurred.", ProblemDetails.instance(request)));
        }
        return handleApi(mapped, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        return validationProblem(List.of(new FieldViolation(ex.getName(), "has an invalid format", simple(ex.getValue()))), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error {} (root cause {})", ex.getClass().getName(), rootCause(ex).getClass().getName());
        return ProblemDetails.response(ProblemDetails.of(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", ProblemDetails.instance(request)));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(violation(error.getField(), error.getDefaultMessage(), error.getRejectedValue()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(FieldViolation.of(error.getObjectName(), error.getDefaultMessage())));
        return validationProblem(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            RequestParam param = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
            String name = param != null && !param.name().isEmpty() ? param.name() : result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(violation(name, error.getDefaultMessage(), result.getArgument()));
            }
        });
        return validationProblem(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationProblem(List.of(FieldViolation.of(ex.getParameterName(), "is required")), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException unknown) {
                return validationProblem(List.of(FieldViolation.of(path(unknown), "is not a known field")), request);
            }
            if (cause instanceof DatabindException databind && !path(databind).isEmpty()) {
                return validationProblem(List.of(FieldViolation.of(path(databind), "has an invalid type or format")), request);
            }
        }
        return ProblemDetails.response(ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST,
                "The request body is missing or is not valid JSON.", path(request)));
    }

    /** Standard Spring MVC errors (404 no handler, 405, 406, 415, ...) get the VEYA format as well. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = switch (statusCode.value()) {
            case 401 -> ErrorCode.AUTHENTICATION_REQUIRED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            default -> statusCode.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
        String detail = switch (statusCode.value()) {
            case 404 -> "The requested resource does not exist.";
            case 405 -> "The HTTP method is not supported for this resource.";
            case 406 -> "The requested media type is not supported.";
            case 415 -> "The request content type is not supported; use application/json.";
            default -> code.title();
        };
        ProblemDetail problem = ProblemDetails.of(code, statusCode, detail, path(request));
        return ResponseEntity.status(statusCode).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private ResponseEntity<Object> validationProblem(List<FieldViolation> errors, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.VALIDATION_FAILED, INVALID_FIELDS, path(request));
        return ProblemDetails.response(ProblemDetails.withErrors(problem, errors));
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    private static FieldViolation violation(String field, String message, Object rejected) {
        boolean secret = field != null && field.toLowerCase(Locale.ROOT).contains("password");
        return new FieldViolation(field, message, secret ? null : simple(rejected));
    }

    /** Only scalar rejected values are echoed (no nested objects). */
    private static Object simple(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof CharSequence ? value : null;
    }

    private static String path(DatabindException ex) {
        StringBuilder sb = new StringBuilder();
        for (var ref : ex.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
