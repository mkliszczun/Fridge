package io.github.mkliszczun.fridge.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } finally {
            int status = wrapper.getStatus();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String principal = (auth != null && auth.isAuthenticated())
                    ? String.valueOf(auth.getPrincipal())
                    : "anonymous";

            boolean hasAuthHeader = request.getHeader("Authorization") != null;

            // ⬇︎ 5 placeholderów i dokładnie 5 argumentów
            log.info("API {} {} -> {} | principal={} | authHeader={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    principal,
                    hasAuthHeader
            );

            wrapper.copyBodyToResponse();
        }
    }
}