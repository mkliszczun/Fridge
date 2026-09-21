package io.github.mkliszczun.fridge.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mkliszczun.fridge.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResponseLoggingFilterTest {
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        events.start();
        root.addAppender(events);
        mvc = MockMvcBuilders.standaloneSetup(new ExampleController())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new ResponseLoggingFilter()).build();
        events.list.clear();
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(events);
        events.stop();
        MDC.clear();
    }

    @Test
    void logsTemplateStatusAndTimeWithFreshIdWithoutSensitiveRequestData() throws Exception {
        MDC.put("requestId", "parent-context");
        var response = mvc.perform(get("/logging-test/private-email@example.com")
                        .queryParam("token", "secret-query").header("Authorization", "Bearer secret-jwt")
                        .header("X-Request-ID", "untrusted-id").content("secret-password"))
                .andExpect(status().isOk()).andExpect(content().string("unchanged-body")).andReturn().getResponse();
        String id = response.getHeader("X-Request-ID");
        assertThatCode(() -> UUID.fromString(id)).doesNotThrowAnyException();
        assertThat(id).isNotEqualTo("untrusted-id");
        assertThat(MDC.get("requestId")).isEqualTo("parent-context");
        var requestEvent = events.list.stream().filter(e -> e.getFormattedMessage().startsWith("event=http_request")).findFirst().orElseThrow();
        assertThat(requestEvent.getLevel()).isEqualTo(Level.INFO);
        assertThat(requestEvent.getMDCPropertyMap()).containsEntry("requestId", id);
        assertThat(requestEvent.getFormattedMessage()).contains("route=/logging-test/{id}", "status=200", "durationMs=")
                .doesNotContain("private-email", "secret-query", "secret-jwt", "secret-password", "unchanged-body");
        String nextId = mvc.perform(get("/logging-test/next")).andReturn().getResponse().getHeader("X-Request-ID");
        assertThat(nextId).isNotEqualTo(id);
    }

    @Test
    void handledServerErrorHasSafeCauseAndSameRequestIdInBothLogs() throws Exception {
        String id = mvc.perform(get("/failure")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Service unavailable"))
                .andReturn().getResponse().getHeader("X-Request-ID");
        var failures = events.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(failures).hasSize(2).allSatisfy(event -> {
            assertThat(event.getMDCPropertyMap()).containsEntry("requestId", id);
            assertThat(event.getFormattedMessage()).doesNotContain("secret-password");
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(failures.get(0).getFormattedMessage()).contains("event=api_failure", "IllegalStateException", "ResponseLoggingFilterTest.java:");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void expectedClientErrorIsWarningWithoutStackTrace() throws Exception {
        mvc.perform(get("/bad-request")).andExpect(status().isBadRequest());
        assertThat(events.list.stream().filter(e -> e.getLevel() == Level.ERROR)).isEmpty();
        assertThat(events.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(1);
    }

    @Test
    void uncaughtFilterFailureIsNotReportedAs200AndContextIsCleared() {
        var request = new MockHttpServletRequest("GET", "/private-path");
        assertThatThrownBy(() -> new ResponseLoggingFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new IOException("secret-password"); })).isInstanceOf(IOException.class);
        assertThat(events.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("status=500"));
        assertThat(events.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain("secret-password", "/private-path"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @RestController
    static class ExampleController {
        @GetMapping("/logging-test/{id}") public String ok() { return "unchanged-body"; }
        @GetMapping("/failure") public String fail() { throw new IllegalStateException("secret-password"); }
        @GetMapping("/bad-request") public String badRequest() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
}
