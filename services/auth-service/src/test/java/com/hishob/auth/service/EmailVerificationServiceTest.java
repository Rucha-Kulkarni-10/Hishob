package com.hishob.auth.service;

import com.hishob.auth.config.EmailVerificationProperties;
import com.hishob.auth.dto.ResendVerificationResponse;
import com.hishob.auth.dto.VerifyEmailResponse;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.email.service.EmailSender;
import com.hishob.auth.entity.EmailVerificationToken;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.InvalidVerificationTokenException;
import com.hishob.auth.repository.EmailVerificationTokenRepository;
import com.hishob.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationProperties properties;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        properties = new EmailVerificationProperties();
        properties.setRequired(true);
        properties.setTokenValiditySeconds(86400); // 24 hours
        properties.setResendCooldownSeconds(60);   // 60 seconds
        properties.setFrontendUrl("http://localhost:3000");
        properties.setVerificationPath("/verify-email");

        emailVerificationService = new EmailVerificationService(
                tokenRepository,
                userRepository,
                emailSender,
                properties
        );
        emailVerificationService.clearResendRateLimitCache();
    }

    @Test
    @DisplayName("createVerificationToken - Should issue a 256-bit URL-safe token and persist only its SHA-256 hash")
    void testCreateVerificationToken_Success() {
        User user = new User("Alice", "alice@example.com", "hashedPassword");
        user.setId(UUID.randomUUID());

        String rawToken = emailVerificationService.createVerificationToken(user);

        assertThat(rawToken).isNotBlank();
        assertThat(rawToken).hasSize(43); // 32 bytes in URL-safe base64 unpadded = 43 chars

        // Verify invalidation of previous active tokens was called
        verify(tokenRepository).invalidateActiveTokensForUser(eq(user), any(Instant.class));

        // Verify saved token hash matches SHA-256 of rawToken
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());

        EmailVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getTokenHash()).isEqualTo(emailVerificationService.hashToken(rawToken));
        assertThat(savedToken.getTokenHash()).hasSize(64); // SHA-256 hex string is 64 chars
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
        assertThat(savedToken.isConsumed()).isFalse();
    }

    @Test
    @DisplayName("verifyEmail - Should successfully verify unconsumed, unexpired token")
    void testVerifyEmail_Success() {
        User user = new User("Bob", "bob@example.com", "hashedPassword");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);

        String rawToken = "sample-raw-token-abc-123";
        String tokenHash = emailVerificationService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(
                tokenHash,
                user,
                Instant.now().plusSeconds(3600)
        );

        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        VerifyEmailResponse response = emailVerificationService.verifyEmail(rawToken);

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getMessage()).contains("Email verified successfully");

        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isNotNull();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();

        verify(tokenRepository).save(token);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("verifyEmail - Should throw InvalidVerificationTokenException when token is not found")
    void testVerifyEmail_NotFound() {
        String rawToken = "nonexistent-token";
        String tokenHash = emailVerificationService.hashToken(rawToken);

        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verifyEmail(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Invalid or expired verification token");
    }

    @Test
    @DisplayName("verifyEmail - Should throw InvalidVerificationTokenException when token is already consumed")
    void testVerifyEmail_AlreadyConsumed() {
        User user = new User("Bob", "bob@example.com", "hashedPassword");
        String rawToken = "already-consumed-token";
        String tokenHash = emailVerificationService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(
                tokenHash,
                user,
                Instant.now().plusSeconds(3600)
        );
        token.consume();

        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.verifyEmail(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Invalid or expired verification token");
    }

    @Test
    @DisplayName("verifyEmail - Should throw InvalidVerificationTokenException when token is expired")
    void testVerifyEmail_Expired() {
        User user = new User("Bob", "bob@example.com", "hashedPassword");
        String rawToken = "expired-token";
        String tokenHash = emailVerificationService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(
                tokenHash,
                user,
                Instant.now().minusSeconds(10) // Expired 10 seconds ago
        );

        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.verifyEmail(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Invalid or expired verification token");
    }

    @Test
    @DisplayName("verifyEmail - Should throw InvalidVerificationTokenException for blank token")
    void testVerifyEmail_BlankToken() {
        assertThatThrownBy(() -> emailVerificationService.verifyEmail("   "))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Invalid or expired verification token");
    }

    @Test
    @DisplayName("resendVerification - Non-existent user returns generic message without sending email")
    void testResendVerification_NonExistentUser() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ResendVerificationResponse response = emailVerificationService.resendVerification("nobody@example.com");

        assertThat(response.getMessage()).isEqualTo(EmailVerificationService.GENERIC_RESEND_MESSAGE);
        verify(emailSender, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerification - Already verified user returns generic message without sending email")
    void testResendVerification_AlreadyVerifiedUser() {
        User user = new User("Charlie", "charlie@example.com", "password");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));

        ResendVerificationResponse response = emailVerificationService.resendVerification("charlie@example.com");

        assertThat(response.getMessage()).isEqualTo(EmailVerificationService.GENERIC_RESEND_MESSAGE);
        verify(emailSender, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerification - Unverified user sends email and returns generic message")
    void testResendVerification_UnverifiedUser_Success() {
        User user = new User("David", "david@example.com", "password");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);

        when(userRepository.findByEmail("david@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        when(emailSender.sendEmail(eq("david@example.com"), anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success("msg-123", "Queued"));

        ResendVerificationResponse response = emailVerificationService.resendVerification("david@example.com");

        assertThat(response.getMessage()).isEqualTo(EmailVerificationService.GENERIC_RESEND_MESSAGE);
        verify(emailSender).sendEmail(eq("david@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerification - Should enforce cooldown and not send second email")
    void testResendVerification_CooldownEnforced() {
        User user = new User("David", "david@example.com", "password");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);

        when(userRepository.findByEmail("david@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        when(emailSender.sendEmail(eq("david@example.com"), anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success("msg-123", "Queued"));

        // First resend request
        ResendVerificationResponse firstResponse = emailVerificationService.resendVerification("david@example.com");
        assertThat(firstResponse.getMessage()).isEqualTo(EmailVerificationService.GENERIC_RESEND_MESSAGE);

        // Immediate second resend request
        ResendVerificationResponse secondResponse = emailVerificationService.resendVerification("david@example.com");
        assertThat(secondResponse.getMessage()).isEqualTo(EmailVerificationService.GENERIC_RESEND_MESSAGE);

        // Email should only be sent once due to in-memory cooldown
        verify(emailSender).sendEmail(eq("david@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerification - DB timestamp cooldown suppresses email delivery")
    void testResendVerification_DbCooldownSuppressesEmail() {
        User user = new User("Eve", "eve@example.com", "password");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);

        EmailVerificationToken recentToken = new EmailVerificationToken(
                "hash",
                user,
                Instant.now().plusSeconds(86400)
        );
        recentToken.setCreatedAt(Instant.now().minusSeconds(10)); // Created 10 seconds ago (cooldown is 60s)

        when(userRepository.findByEmail("eve@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(recentToken));

        ResendVerificationResponse response = emailVerificationService.resendVerification("eve@example.com");

        assertThat(response.getMessage()).isEqualTo(EmailVerificationService.GENERIC_RESEND_MESSAGE);
        verify(emailSender, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("buildVerificationLink - Correctly constructs and encodes verification URL")
    void testBuildVerificationLink() {
        String rawToken = "token_with+special/chars==";
        String link = emailVerificationService.buildVerificationLink(rawToken);

        assertThat(link).startsWith("http://localhost:3000/verify-email?token=");
        assertThat(link).contains("token_with%2Bspecial%2Fchars%3D%3D");
    }
}
