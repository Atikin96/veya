package com.veya.shared.persistence;

import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/**
 * Maps errors raised by the database (guard triggers and routines, error codes VYnnn - docs/database/README.md)
 * to API errors: {@code VY400 -> 400}, {@code VY404 -> 404}, {@code VY409 / VY001 -> 409}. Integrity violations the
 * API did not pre-check become 400. Only the primary message of VY errors is used: those messages contain no
 * personal data, unlike the DETAIL part of constraint violations, which is never returned or logged.
 */
public final class DatabaseErrors {

    private DatabaseErrors() {
    }

    public static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    public static String constraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return psql.getServerErrorMessage().getConstraint();
            }
        }
        return null;
    }

    /**
     * Message safe to log for an unexpected error: only programming errors (SQL state class 42, syntax/undefined
     * objects) include the server message, which then names SQL objects, never user values.
     */
    public static String loggableDetail(Throwable ex) {
        String state = sqlState(ex);
        return state != null && state.startsWith("42") ? serverMessage(ex) : "";
    }

    private static String serverMessage(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return psql.getServerErrorMessage().getMessage();
            }
        }
        return null;
    }

    /** @return the mapped error, or {@code null} for an unexpected database error (500). */
    public static ApiException toApiException(DataAccessException ex) {
        String state = sqlState(ex);
        if (state == null) {
            return null;
        }
        return switch (state) {
            case "VY400" -> new ApiException(ErrorCode.VALIDATION_FAILED, serverMessage(ex));
            case "VY404" -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, serverMessage(ex));
            case "VY409" -> new ApiException(ErrorCode.STALE_PLAN_VERSION, serverMessage(ex));
            case "VY001" -> new ApiException(ErrorCode.IMMUTABLE_HISTORY, serverMessage(ex));
            // check, foreign key, not-null and value-format violations that request validation did not catch
            case "23514", "23503", "23502", "22001", "22003", "22P02", "22007", "22008" ->
                    new ApiException(ErrorCode.VALIDATION_FAILED, "One or more values are not accepted.");
            default -> null;
        };
    }
}
