package com.example.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtil {
    private final int expiration = 1000 * 60 * 60; // 1h
    private final String secret = "MySuperStrongSecretKeyWithAtLeast32Chars123456";

    private SecretKey getSigningKey(){
        return new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    }

    public String generateToken(String username, UUID userId, List<String> roles){
        return Jwts.builder()
                .setSubject(username)
                .claim("uid", userId != null ? userId.toString() : null)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
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

    private Claims parser(String token){
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody();
    }
}
