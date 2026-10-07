package com.hishob.auth.service;

import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.LoginRequest;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.dto.UserResponse;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.DuplicateEmailException;
import com.hishob.auth.exception.InvalidCredentialsException;
import com.hishob.auth.exception.ResourceNotFoundException;
import com.hishob.auth.repository.UserRepository;
import com.hishob.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Should successfully register a new user and hash password")
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "secretPassword");
        UUID userId = UUID.randomUUID();
        User savedUser = new User(userId, "Alice", "alice@example.com", "hashedSecretPassword");
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secretPassword")).thenReturn("hashedSecretPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(eq(userId), eq("alice@example.com"), eq("Alice"))).thenReturn("jwt-token-xyz");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("jwt-token-xyz");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getId()).isEqualTo(userId);
        assertThat(response.getUser().getName()).isEqualTo("Alice");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");

        verify(passwordEncoder).encode("secretPassword");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateEmailException when email is already in use")
    void testRegister_DuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Bob", "bob@example.com", "secretPassword");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("bob@example.com");
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest("alice@example.com", "secretPassword");
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Alice", "alice@example.com", "hashedSecretPassword");
        user.setCreatedAt(Instant.now());

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secretPassword", "hashedSecretPassword")).thenReturn(true);
        when(jwtService.generateToken(eq(userId), eq("alice@example.com"), eq("Alice"))).thenReturn("valid-jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("valid-jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when password does not match")
    void testLogin_InvalidPassword() {
        LoginRequest request = new LoginRequest("alice@example.com", "wrongPassword");
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hashedSecretPassword");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashedSecretPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when email is not found")
    void testLogin_UserNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should retrieve current user by email")
    void testGetCurrentUser_Success() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Charlie", "charlie@example.com", "hashedPass");
        user.setCreatedAt(Instant.now());

        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));

        UserResponse response = authService.getCurrentUser("charlie@example.com");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getName()).isEqualTo("Charlie");
        assertThat(response.getEmail()).isEqualTo("charlie@example.com");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when current user not found")
    void testGetCurrentUser_NotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

}
