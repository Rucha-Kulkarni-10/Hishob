package com.hishob.auth.service;

import com.hishob.auth.config.EmailVerificationProperties;
import com.hishob.auth.dto.ResendVerificationResponse;
import com.hishob.auth.dto.VerifyEmailResponse;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.email.service.EmailSender;
import com.hishob.auth.email.service.MailgunEmailSender;
import com.hishob.auth.entity.EmailVerificationToken;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.InvalidVerificationTokenException;
import com.hishob.auth.repository.EmailVerificationTokenRepository;
import com.hishob.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing user email verification token lifecycles, hashing, delivery,
 * consumption, and resend rate limiting.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    public static final String GENERIC_RESEND_MESSAGE =
            "If an account with that email exists and requires verification, a verification email has been sent.";

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final EmailVerificationProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Instant> resendRateLimitCache = new ConcurrentHashMap<>();

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailSender emailSender,
            EmailVerificationProperties properties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    /**
     * Issues a cryptographically secure 256-bit verification token for the user.
     * Invalidates any previously active tokens for this user before issuing a new one.
     * Only the SHA-256 hash of the token is persisted.
     *
     * @param user the recipient user
     * @return raw URL-safe token (only for embedding in outbound email link, never logged or stored)
     */
    @Transactional
    public String createVerificationToken(User user) {
        Instant now = Instant.now();
        // Safely invalidate any previously active tokens for this user
        tokenRepository.invalidateActiveTokensForUser(user, now);

        // Generate 256 bits of cryptographically secure entropy
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = hashToken(rawToken);
        Instant expiresAt = now.plusSeconds(properties.getTokenValiditySeconds());

        EmailVerificationToken tokenEntity = new EmailVerificationToken(tokenHash, user, expiresAt);
        tokenRepository.save(tokenEntity);

        log.info("Issued email verification token for user [{}] expiring at [{}]", user.getId(), expiresAt);
        return rawToken;
    }

    /**
     * Computes standard SHA-256 hex digest of a raw token.
     */
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in runtime", e);
        }
    }

    /**
     * Constructs verification link from configured base URL, path, and URL-encoded token.
     */
    public String buildVerificationLink(String rawToken) {
        String baseUrl = properties.getFrontendUrl() != null
                ? properties.getFrontendUrl().replaceAll("/+$", "")
                : "http://localhost:3000";
        String path = properties.getVerificationPath() != null
                ? properties.getVerificationPath().trim()
                : "/verify-email";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return baseUrl + path + "?token=" + encodedToken;
    }

    /**
     * Dispatches a branded verification email to the user with both plain-text and HTML formats.
     */
    public EmailSendResult sendVerificationEmail(User user, String rawToken) {
        String verificationLink = buildVerificationLink(rawToken);
        long validityHours = Math.max(1, properties.getTokenValiditySeconds() / 3600);

        String subject = "Verify your email for HISHOB";

        String textBody = "Hello " + user.getName() + ",\n\n"
                + "Welcome to HISHOB! Please verify your email address to complete your registration.\n\n"
                + "Click the link below to verify your email:\n"
                + verificationLink + "\n\n"
                + "This verification link will expire in " + validityHours + " hours.\n\n"
                + "If you did not create an account on HISHOB, please ignore this email.\n\n"
                + "Best regards,\n"
                + "The HISHOB Team";

        String htmlBody = buildHtmlBody(user.getName(), verificationLink, validityHours);

        log.info("Dispatching email verification to [{}]", MailgunEmailSender.maskEmail(user.getEmail()));
        return emailSender.sendEmail(user.getEmail(), subject, textBody, htmlBody);
    }

    /**
     * Builds HISHOB-branded HTML template for email verification.
     */
    private String buildHtmlBody(String name, String verificationLink, long validityHours) {
        String safeName = HtmlUtils.htmlEscape(name != null ? name : "there");
        String safeLink = HtmlUtils.htmlEscape(verificationLink);

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>Verify your email</title>\n"
                + "</head>\n"
                + "<body style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b;\">\n"
                + "  <div style=\"max-width: 560px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;\">\n"
                + "    <div style=\"background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%); padding: 32px 24px; text-align: center;\">\n"
                + "      <h1 style=\"color: #ffffff; margin: 0; font-size: 26px; font-weight: 700; letter-spacing: 0.5px;\">HISHOB</h1>\n"
                + "    </div>\n"
                + "    <div style=\"padding: 32px 28px;\">\n"
                + "      <h2 style=\"font-size: 20px; font-weight: 600; margin-top: 0; margin-bottom: 16px; color: #0f172a;\">Verify your email address</h2>\n"
                + "      <p style=\"font-size: 15px; line-height: 1.6; color: #334155; margin-bottom: 12px;\">Hello <strong>" + safeName + "</strong>,</p>\n"
                + "      <p style=\"font-size: 15px; line-height: 1.6; color: #334155; margin-bottom: 24px;\">Thank you for joining HISHOB. Please verify your email address to complete your registration and activate your account:</p>\n"
                + "      <div style=\"text-align: center; margin: 32px 0;\">\n"
                + "        <a href=\"" + safeLink + "\" style=\"background-color: #4f46e5; color: #ffffff; text-decoration: none; padding: 12px 32px; border-radius: 6px; font-size: 15px; font-weight: 600; display: inline-block;\">Verify Email Address</a>\n"
                + "      </div>\n"
                + "      <p style=\"font-size: 13px; line-height: 1.5; color: #64748b; margin-bottom: 8px;\">If the button above doesn't work, copy and paste this link into your web browser:</p>\n"
                + "      <p style=\"font-size: 12px; word-break: break-all; color: #4f46e5; background-color: #f1f5f9; padding: 10px 14px; border-radius: 6px; margin-bottom: 24px;\">" + safeLink + "</p>\n"
                + "      <p style=\"font-size: 13px; color: #64748b; margin-bottom: 16px;\">This link is valid for <strong>" + validityHours + " hours</strong>.</p>\n"
                + "      <p style=\"font-size: 13px; color: #94a3b8; margin-bottom: 0;\">If you did not create an account on HISHOB, you can safely ignore this email.</p>\n"
                + "    </div>\n"
                + "    <div style=\"border-top: 1px solid #f1f5f9; padding: 16px 28px; text-align: center; font-size: 12px; color: #94a3b8; background-color: #fafafa;\">\n"
                + "      &copy; 2026 HISHOB. All rights reserved.\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * Atomically consumes a verification token and marks the associated user as verified.
     * Prevents replay and concurrent reuse via pessimistic locking.
     *
     * @param rawToken raw verification token provided by the user
     * @return VerifyEmailResponse confirming successful verification
     * @throws InvalidVerificationTokenException if token is invalid, expired, or already consumed
     */
    @Transactional
    public VerifyEmailResponse verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException("Invalid or expired verification token");
        }

        String tokenHash = hashToken(rawToken);

        // Pessimistic write lock ensures atomic check-and-consume across concurrent requests
        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token"));

        if (token.isConsumed() || token.isExpired()) {
            throw new InvalidVerificationTokenException("Invalid or expired verification token");
        }

        Instant now = Instant.now();
        token.setConsumedAt(now);
        tokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        userRepository.save(user);

        log.info("Email verified successfully for user [{}]", user.getId());
        return new VerifyEmailResponse(true, "Email verified successfully. You can now log in.");
    }

    /**
     * Resends a verification email while protecting against account enumeration,
     * Mailgun quota exhaustion, and abuse via sliding cooldown.
     *
     * Always returns an identical generic response regardless of whether the account
     * exists, is already verified, or is in cooldown.
     *
     * @param email the email address to resend verification to
     * @return generic success response
     */
    @Transactional
    public ResendVerificationResponse resendVerification(String email) {
        if (email == null || email.isBlank()) {
            return new ResendVerificationResponse(GENERIC_RESEND_MESSAGE);
        }

        String normalizedEmail = email.trim().toLowerCase();
        Instant now = Instant.now();

        // 1. In-memory sliding rate limit / cooldown check (protects both existent and non-existent accounts)
        Instant lastAttempt = resendRateLimitCache.get(normalizedEmail);
        if (lastAttempt != null && Duration.between(lastAttempt, now).getSeconds() < properties.getResendCooldownSeconds()) {
            log.debug("Resend suppressed by in-memory cooldown for [{}]", MailgunEmailSender.maskEmail(normalizedEmail));
            return new ResendVerificationResponse(GENERIC_RESEND_MESSAGE);
        }

        // 2. Query user
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            resendRateLimitCache.put(normalizedEmail, now);
            log.debug("Resend requested for non-existent account [{}]", MailgunEmailSender.maskEmail(normalizedEmail));
            return new ResendVerificationResponse(GENERIC_RESEND_MESSAGE);
        }

        User user = userOpt.get();

        // 3. If already verified, do not send, do not reveal status
        if (user.isEmailVerified()) {
            resendRateLimitCache.put(normalizedEmail, now);
            log.debug("Resend requested for already verified account [{}]", user.getId());
            return new ResendVerificationResponse(GENERIC_RESEND_MESSAGE);
        }

        // 4. In-database token timestamp cooldown check (ensures persistent cooldown across restarts)
        Optional<EmailVerificationToken> latestTokenOpt = tokenRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (latestTokenOpt.isPresent()) {
            EmailVerificationToken latestToken = latestTokenOpt.get();
            if (latestToken.getCreatedAt().plusSeconds(properties.getResendCooldownSeconds()).isAfter(now)) {
                resendRateLimitCache.put(normalizedEmail, now);
                log.debug("Resend suppressed by persistent DB cooldown for user [{}]", user.getId());
                return new ResendVerificationResponse(GENERIC_RESEND_MESSAGE);
            }
        }

        // Record cooldown timestamp
        resendRateLimitCache.put(normalizedEmail, now);

        // 5. Invalidate previous active tokens and issue a fresh verification token
        String rawToken = createVerificationToken(user);

        // 6. Send email safely without leaking provider errors
        try {
            sendVerificationEmail(user, rawToken);
        } catch (Exception e) {
            log.error("Failed to send verification email for user [{}]: {}", user.getId(), e.getMessage());
        }

        return new ResendVerificationResponse(GENERIC_RESEND_MESSAGE);
    }

    /**
     * Resets in-memory rate limiting cache (primarily for test isolation).
     */
    public void clearResendRateLimitCache() {
        resendRateLimitCache.clear();
    }
}
