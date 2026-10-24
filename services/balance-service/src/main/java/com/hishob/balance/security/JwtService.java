package com.hishob.balance.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @PostConstruct
    void validateConfiguredSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is not configured; set the JWT_SECRET environment variable for this service");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HMAC-SHA256 signing");
        }
        String value = secret.toLowerCase();
        if (value.contains("your-") || value.contains("replace-with") || value.contains("changeme")
                || value.contains("change-me") || value.contains("placeholder") || value.contains("example-")) {
            throw new IllegalStateException("jwt.secret still holds a template placeholder value");
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        String userIdString = claims.get("userId", String.class);
        if (userIdString == null) {
            throw new JwtException("JWT missing userId claim");
        }
        return UUID.fromString(userIdString);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

}
