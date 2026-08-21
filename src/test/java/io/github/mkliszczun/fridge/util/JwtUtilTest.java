package io.github.mkliszczun.fridge.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "MySuperStrongSecretKeyWithAtLeast32Chars123456";
    private static final int EXPIRATION = 3_600_000;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpiration(EXPIRATION);
        jwtUtil = new JwtUtil(properties);
    }

    @Test
    void generatesAndReadsConfiguredToken() {
        UUID userId = UUID.randomUUID();
        List<String> roles = List.of("USER", "ADMIN");

        String token = jwtUtil.generateToken("alice", userId, roles);

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.extractUserId(token)).contains(userId);
        assertThat(jwtUtil.extractRoles(token)).containsExactly("USER", "ADMIN");
    }

    @Test
    void usesConfiguredExpiration() {
        long beforeGeneration = System.currentTimeMillis();

        String token = jwtUtil.generateToken("alice", List.of("USER"));

        long afterGeneration = System.currentTimeMillis();
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getExpiration().getTime())
                .isBetween(beforeGeneration + EXPIRATION - 1_000, afterGeneration + EXPIRATION);
    }
}
