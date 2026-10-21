package com.hishob.auth.service;

import com.hishob.auth.config.EmailVerificationProperties;
import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.LoginRequest;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.dto.ResendVerificationRequest;
import com.hishob.auth.dto.ResendVerificationResponse;
import com.hishob.auth.dto.UserResponse;
import com.hishob.auth.dto.VerifyEmailRequest;
import com.hishob.auth.dto.VerifyEmailResponse;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.DuplicateEmailException;
import com.hishob.auth.exception.EmailNotVerifiedException;
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
import static org.mockito.Mockito.never;
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

    @Mock
    private EmailVerificationService emailVerificationService;

    private EmailVerificationProperties emailVerificationProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        emailVerificationProperties = new EmailVerificationProperties();
        emailVerificationProperties.setRequired(false);
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                emailVerificationService,
                emailVerificationProperties
        );
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

    @Test
    @DisplayName("Should return verification required response and no JWT when verification is required")
    void testRegister_WhenVerificationRequired_Success() {
        emailVerificationProperties.setRequired(true);

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "secretPassword");
        UUID userId = UUID.randomUUID();
        User savedUser = new User(userId, "Alice", "alice@example.com", "hashedSecretPassword");
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secretPassword")).thenReturn("hashedSecretPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(emailVerificationService.createVerificationToken(savedUser)).thenReturn("raw-token-123");
        when(emailVerificationService.sendVerificationEmail(savedUser, "raw-token-123"))
                .thenReturn(EmailSendResult.success("msg-1", "Queued"));

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.isEmailVerificationRequired()).isTrue();
        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getTokenType()).isNull();
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getMessage()).contains("Registration successful. Please check your email");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("Should return safe retry message when email sending fails or is skipped")
    void testRegister_WhenVerificationRequired_EmailSendingFails() {
        emailVerificationProperties.setRequired(true);

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "secretPassword");
        UUID userId = UUID.randomUUID();
        User savedUser = new User(userId, "Alice", "alice@example.com", "hashedSecretPassword");
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secretPassword")).thenReturn("hashedSecretPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(emailVerificationService.createVerificationToken(savedUser)).thenReturn("raw-token-123");
        when(emailVerificationService.sendVerificationEmail(savedUser, "raw-token-123"))
                .thenReturn(EmailSendResult.skipped("Disabled"));

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.isEmailVerificationRequired()).isTrue();
        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getMessage()).contains("resend verification option");
    }

    @Test
    @DisplayName("Should throw EmailNotVerifiedException when logging in unverified with verification required")
    void testLogin_WhenVerificationRequired_UnverifiedUser_ThrowsException() {
        emailVerificationProperties.setRequired(true);

        LoginRequest request = new LoginRequest("alice@example.com", "secretPassword");
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", "hashedSecretPassword");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secretPassword", "hashedSecretPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("Email is not verified");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("Should succeed with JWT when logging in verified user with verification required")
    void testLogin_WhenVerificationRequired_VerifiedUser_Success() {
        emailVerificationProperties.setRequired(true);

        LoginRequest request = new LoginRequest("alice@example.com", "secretPassword");
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Alice", "alice@example.com", "hashedSecretPassword");
        user.setEmailVerified(true);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secretPassword", "hashedSecretPassword")).thenReturn(true);
        when(jwtService.generateToken(eq(userId), eq("alice@example.com"), eq("Alice"))).thenReturn("valid-jwt");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("valid-jwt");
        assertThat(response.getUser().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Should allow unverified user to login when verification is not required")
    void testLogin_WhenVerificationDisabled_UnverifiedUser_Success() {
        emailVerificationProperties.setRequired(false);

        LoginRequest request = new LoginRequest("alice@example.com", "secretPassword");
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Alice", "alice@example.com", "hashedSecretPassword");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secretPassword", "hashedSecretPassword")).thenReturn(true);
        when(jwtService.generateToken(eq(userId), eq("alice@example.com"), eq("Alice"))).thenReturn("valid-jwt");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("valid-jwt");
    }

    @Test
    @DisplayName("verifyEmail should delegate to emailVerificationService")
    void testVerifyEmail_DelegatesToService() {
        VerifyEmailRequest request = new VerifyEmailRequest("test-token");
        VerifyEmailResponse expectedResponse = new VerifyEmailResponse(true, "Email verified successfully");

        when(emailVerificationService.verifyEmail("test-token")).thenReturn(expectedResponse);

        VerifyEmailResponse response = authService.verifyEmail(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(emailVerificationService).verifyEmail("test-token");
    }

    @Test
    @DisplayName("resendVerification should delegate to emailVerificationService")
    void testResendVerification_DelegatesToService() {
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");
        ResendVerificationResponse expectedResponse = new ResendVerificationResponse("If an account exists...");

        when(emailVerificationService.resendVerification("test@example.com")).thenReturn(expectedResponse);

        ResendVerificationResponse response = authService.resendVerification(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(emailVerificationService).resendVerification("test@example.com");
    }

}
