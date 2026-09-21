package io.github.mkliszczun.fridge.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String previousRequestId = MDC.get("requestId");
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failed = true;
            log.error("event=request_unhandled_failure diagnostics={}", SafeDiagnostics.describe(ex));
            throw ex;
        } finally {
            try {
                int status = failed ? 500 : response.getStatus();
                // Log the route template, not path parameters, query strings, identity or bodies.
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String pattern = route == null ? "unmapped" : route.toString();
                String method = request.getMethod().replaceAll("[^A-Z]", "");
                var level = status >= 500 ? org.slf4j.event.Level.ERROR
                        : status >= 400 ? org.slf4j.event.Level.WARN
                        : "/health".equals(pattern) ? org.slf4j.event.Level.DEBUG : org.slf4j.event.Level.INFO;
                log.atLevel(level).log("event=http_request method={} route={} status={} durationMs={}",
                        method, pattern, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            } finally {
                if (previousRequestId == null) MDC.remove("requestId");
                else MDC.put("requestId", previousRequestId);
            }
        }
    }
}
