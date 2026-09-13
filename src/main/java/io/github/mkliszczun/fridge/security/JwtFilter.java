package io.github.mkliszczun.fridge.security;

import io.github.mkliszczun.fridge.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final JpaUserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, JpaUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws IOException, ServletException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = authHeader.substring(7);

            try {
                var claims = jwtUtil.parser(token);
                if (!"fridge".equals(claims.getIssuer()) || !"access".equals(claims.get("type"))
                        || claims.getExpiration() == null || claims.get("ver") == null || claims.get("uid") == null) {
                    throw new IllegalArgumentException("Invalid access token");
                }
                var userDetails = userDetailsService.loadById(
                        java.util.UUID.fromString(claims.get("uid", String.class)));
                new org.springframework.security.authentication.AccountStatusUserDetailsChecker().check(userDetails);
                if (claims.get("ver", Number.class).longValue() != userDetails.getTokenVersion()) {
                    throw new IllegalArgumentException("Revoked access token");
                }

                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException
                     | org.springframework.security.core.AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

}
