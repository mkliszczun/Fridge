package io.github.mkliszczun.fridge.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    @jakarta.annotation.PostConstruct
    void validateConfiguration() {
        getSigningKey();
        if (jwtProperties.getExpiration() <= 0) throw new IllegalStateException("JWT expiration must be positive");
    }

    private SecretKey getSigningKey(){
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, UUID userId, List<String> roles){
        return generateToken(username, userId, roles, 0);
    }

    public String generateToken(String username, UUID userId, List<String> roles, long version){
        return Jwts.builder()
                .setIssuer("fridge")
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .claim("type", "access")
                .claim("ver", version)
                .claim("uid", userId != null ? userId.toString() : null)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(String username, List<String> roles){
        return generateToken(username, null, roles);
    }

    public String extractUsername(String token){
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateToken(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        }catch (Exception e){
            return false;
        }
    }
@SuppressWarnings("unchecked cast")
    public List<String> extractRoles(String token){
        return (List<String>) Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("roles", List.class);
    }

    public Optional<UUID> extractUserId(String token){
        String uid = parser(token).get("uid", String.class);
        return (uid == null || uid.isBlank()) ? Optional.empty() : Optional.of(UUID.fromString(uid));
    }

    public Claims parser(String token){
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody();
    }
}
