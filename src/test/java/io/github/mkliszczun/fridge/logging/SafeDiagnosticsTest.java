package io.github.mkliszczun.fridge.logging;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeDiagnosticsTest {
    @Test
    void keepsCauseLocationAndSqlStateButNotMessagesOrSql() {
        SQLException sql = new SQLException("SELECT password FROM users; secret-token", "23505");
        var error = new IllegalStateException("user@example.com", sql);
        String diagnostics = SafeDiagnostics.describe(error);
        assertThat(diagnostics).contains("IllegalStateException", "SQLException", "sqlState=23505", "SafeDiagnosticsTest.java:")
                .doesNotContain("SELECT", "secret-token", "user@example.com");
    }

    @Test
    void keepsSmtpNestedExceptionTypesWithoutAddressesCodesOrMailContents() {
        var nested = new MessagingException("smtp-password");
        nested.setNextException(new java.net.SocketTimeoutException("verification code 123456"));
        var error = new MailSendException("user@example.com", null, Map.of("private mail body", nested));
        assertThat(SafeDiagnostics.describe(error)).contains("MailSendException", "MessagingException", "SocketTimeoutException")
                .doesNotContain("smtp-password", "123456", "user@example.com", "private mail body");
    }

    @Test
    void reportsOnlyNumericUpstreamStatusAndTerminatesOnCycles() {
        var http = WebClientResponseException.create(503, "private status", null,
                "private provider body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(SafeDiagnostics.describe(http)).contains("upstreamStatus=503")
                .doesNotContain("private status", "private provider body");
        var rest = new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "private status", null,
                "private provider body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(SafeDiagnostics.describe(new io.github.mkliszczun.fridge.exception.AiServiceUnavailableException("safe", rest)))
                .contains("upstreamStatus=429", "AiServiceUnavailableException")
                .doesNotContain("private status", "private provider body");
        var first = new RuntimeException("private one");
        var second = new RuntimeException("private two", first);
        first.initCause(second);
        assertThat(SafeDiagnostics.describe(first)).doesNotContain("private one", "private two");
    }
}
