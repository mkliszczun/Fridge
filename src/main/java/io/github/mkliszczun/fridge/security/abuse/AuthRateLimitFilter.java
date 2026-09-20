package io.github.mkliszczun.fridge.security.abuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/auth/login", "/auth/register", "/auth/refresh",
            "/auth/password/forgot", "/auth/password/reset", "/auth/email/send", "/auth/email/verify");
    private final AuthRateLimiter limiter;
    private final AbuseProperties properties;
    private final ObjectMapper json;

    public AuthRateLimitFilter(AuthRateLimiter limiter, AbuseProperties properties, ObjectMapper json) {
        this.limiter = limiter;
        this.properties = properties;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!properties.isEnabled() || !"POST".equals(request.getMethod()) || !PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        String address = properties.isTrustFlyProxy() ? request.getHeader("Fly-Client-IP") : request.getRemoteAddr();
        // Never use X-Forwarded-For. Validate literals before InetAddress to avoid DNS lookups.
        if (address == null || address.length() > 45 || !address.matches("[0-9a-fA-F:.]+")) {
            response.sendError(400, "Invalid client address");
            return;
        }
        byte[] bytes;
        try {
            bytes = InetAddress.getByName(address).getAddress();
        } catch (IOException invalidAddress) {
            response.sendError(400, "Invalid client address");
            return;
        }
        // Treat IPv6 /64 as one client; rotating interface addresses must not reset the limit.
        String client = HexFormat.of().formatHex(bytes.length == 16 ? Arrays.copyOf(bytes, 8) : bytes);
        long retry = limiter.check(path, client);
        if (retry > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retry));
            response.setContentType("application/json");
            json.writeValue(response.getWriter(), ErrorResponse.of("Too many authentication requests"));
            return;
        }
        chain.doFilter(request, response);
    }
}
