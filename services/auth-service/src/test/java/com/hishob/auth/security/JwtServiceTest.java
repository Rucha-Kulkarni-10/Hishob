package com.hishob.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "0123456789012345678901234567890123456789012345678901234567890123"; // 64 chars
    private final long testExpiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", testSecret);
        ReflectionTestUtils.setField(jwtService, "expiration", testExpiration);
    }

    @Test
    @DisplayName("Should generate valid JWT token with subject and claims")
    void testGenerateToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String name = "Test User";

        String token = jwtService.generateToken(userId, email, name);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("Should validate token against matching UserDetails")
    void testValidateToken_Success() {
        UUID userId = UUID.randomUUID();
        String email = "valid@example.com";
        String name = "Valid User";

        String token = jwtService.generateToken(userId, email, name);
        UserDetails userDetails = new User(email, "password", Collections.emptyList());

        boolean isValid = jwtService.isTokenValid(token, userDetails);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject token when UserDetails username does not match")
    void testValidateToken_Mismatch() {
        UUID userId = UUID.randomUUID();
        String email = "user1@example.com";
        String name = "User One";

        String token = jwtService.generateToken(userId, email, name);
        UserDetails otherUser = new User("user2@example.com", "password", Collections.emptyList());

        boolean isValid = jwtService.isTokenValid(token, otherUser);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should detect expired token")
    void testExpiredToken() {
        JwtService shortLivedJwtService = new JwtService();
        ReflectionTestUtils.setField(shortLivedJwtService, "secret", testSecret);
        ReflectionTestUtils.setField(shortLivedJwtService, "expiration", -1000L); // already expired

        UUID userId = UUID.randomUUID();
        String email = "expired@example.com";
        String name = "Expired User";

        String token = shortLivedJwtService.generateToken(userId, email, name);
        UserDetails userDetails = new User(email, "password", Collections.emptyList());

        assertThat(jwtService.isTokenExpired(token)).isTrue();
        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

}
