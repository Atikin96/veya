package com.veya.shared;

import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.persistence.SqlLike;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import com.veya.shared.web.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Shared infrastructure: database error mapping, LIKE escaping, error codes, request ids")
class SharedUnitTest {

    private static PSQLException server(String state, String message, String constraint) {
        String fields = "SERROR\0C" + state + "\0M" + message + "\0" + (constraint == null ? "" : "n" + constraint + "\0");
        return new PSQLException(new ServerErrorMessage(fields));
    }

    @ParameterizedTest(name = "SQL state {0} -> {1}")
    @CsvSource({"VY400, VALIDATION_FAILED", "VY404, RESOURCE_NOT_FOUND", "VY409, STALE_PLAN_VERSION",
            "VY001, IMMUTABLE_HISTORY", "23514, VALIDATION_FAILED", "23503, VALIDATION_FAILED", "22P02, VALIDATION_FAILED"})
    @DisplayName("Database error codes map to the documented API errors (VY400 -> 400, VY404 -> 404, VY409/VY001 -> 409)")
    void mapsDatabaseErrors(String state, ErrorCode expected) {
        ApiException mapped = DatabaseErrors.toApiException(new DataIntegrityViolationException("x", server(state, "Plan not found", null)));
        assertThat(mapped).isNotNull();
        assertThat(mapped.code()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Only VY messages are passed on; constraint details (may contain values) and unknown states are not")
    void safeMessages() {
        ApiException vy = DatabaseErrors.toApiException(new DataIntegrityViolationException("x", server("VY404", "Proposal not found", null)));
        assertThat(vy.getMessage()).isEqualTo("Proposal not found");
        ApiException check = DatabaseErrors.toApiException(new DataIntegrityViolationException("x",
                server("23514", "new row violates check constraint with secret value", "food_entry_kcal_range")));
        assertThat(check.getMessage()).doesNotContain("secret");
        assertThat(DatabaseErrors.toApiException(new DataAccessResourceFailureException("down", new SQLException("x", "08006")))).isNull();
        assertThat(DatabaseErrors.toApiException(new DataAccessResourceFailureException("no state"))).isNull();
        assertThat(DatabaseErrors.constraintName(new DataIntegrityViolationException("x", server("23505", "dup", "app_user_email_uq"))))
                .isEqualTo("app_user_email_uq");
        assertThat(DatabaseErrors.loggableDetail(new DataIntegrityViolationException("x", server("42703", "column x does not exist", null))))
                .isEqualTo("column x does not exist");
        assertThat(DatabaseErrors.loggableDetail(new DataIntegrityViolationException("x", server("23505", "Key (email)=(a@b.c)", null))))
                .isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {"Chick|chick", "50%|50\\%", "a_b|a\\_b", "back\\slash|back\\\\slash"})
    @DisplayName("LIKE escaping: user text is matched literally (wildcards and the escape character are escaped)")
    void sqlLike(String input, String expected) {
        assertThat(SqlLike.escape(input)).isEqualTo(expected);
        assertThat(SqlLike.escape(null)).isNull();
    }

    @Test
    @DisplayName("Every error code has a unique problem type URI and a 4xx/5xx status")
    void errorCodes() {
        Set<String> types = new HashSet<>();
        Arrays.stream(ErrorCode.values()).forEach(code -> {
            assertThat(types.add(code.type().toString())).isTrue();
            assertThat(code.status().value()).isBetween(400, 599);
        });
        assertThat(ErrorCode.VALIDATION_FAILED.type()).hasToString("https://veya.example/problems/validation-failed");
    }

    @Test
    @DisplayName("Request id: a safe client id is kept, an unsafe one replaced; MDC is cleared after the request")
    void requestIds() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(RequestIdFilter.HEADER, "client-id-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(RequestIdFilter.current());
            }
        });
        assertThat(seen.get()).isEqualTo("client-id-12345");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-id-12345");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        MockHttpServletRequest unsafe = new MockHttpServletRequest("GET", "/api/v1/me");
        unsafe.addHeader(RequestIdFilter.HEADER, "bad id\nwith newline");
        MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
        filter.doFilter(unsafe, unsafeResponse, new MockFilterChain());
        assertThat(unsafeResponse.getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }
}
