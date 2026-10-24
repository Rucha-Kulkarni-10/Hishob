package com.hishob.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.config.EmailVerificationProperties;
import com.hishob.auth.dto.LoginRequest;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.dto.ResendVerificationRequest;
import com.hishob.auth.dto.VerifyEmailRequest;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.email.service.EmailSender;
import com.hishob.auth.entity.EmailVerificationToken;
import com.hishob.auth.entity.User;
import com.hishob.auth.repository.EmailVerificationTokenRepository;
import com.hishob.auth.repository.UserRepository;
import com.hishob.auth.service.EmailVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class EmailVerificationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationProperties emailVerificationProperties;

    @MockitoBean
    private EmailSender emailSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        tokenRepository.deleteAll();
        userRepository.deleteAll();
        emailVerificationService.clearResendRateLimitCache();

        // Default mock behavior for email delivery
        when(emailSender.sendEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.success("mailgun-msg-123", "Queued"));
    }

    @AfterEach
    void tearDown() {
        emailVerificationProperties.setRequired(false);
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Registration with enforcement enabled creates unverified user and returns verificationRequired response without JWT")
    void testRegister_EnforcementEnabled_ReturnsVerificationRequired() throws Exception {
        emailVerificationProperties.setRequired(true);

        RegisterRequest request = new RegisterRequest("Alice Enforced", "alice.enforced@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("alice.enforced@example.com"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.message").value("Registration successful. Please check your email to verify your account."));

        // Verify user in DB is unverified
        User user = userRepository.findByEmail("alice.enforced@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();

        // Verify verification token exists in DB (hashed)
        List<EmailVerificationToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getUser().getId()).isEqualTo(user.getId());
        assertThat(tokens.get(0).getTokenHash()).hasSize(64);
        assertThat(tokens.get(0).isConsumed()).isFalse();

        // Verify email was dispatched
        verify(emailSender, times(1)).sendEmail(eq("alice.enforced@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Unverified login is blocked with 403 Forbidden when enforcement is enabled")
    void testLogin_UnverifiedUser_BlockedWhenEnforcementEnabled() throws Exception {
        emailVerificationProperties.setRequired(true);

        // Register user
        RegisterRequest registerRequest = new RegisterRequest("Bob Blocked", "bob.blocked@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Attempt login while unverified
        LoginRequest loginRequest = new LoginRequest("bob.blocked@example.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Email is not verified. Please verify your email before logging in."));
    }

    @Test
    @DisplayName("Full verification flow: register -> verify token -> login succeeds with JWT")
    void testFullVerificationFlow_Success() throws Exception {
        emailVerificationProperties.setRequired(true);

        // 1. Register unverified user
        RegisterRequest registerRequest = new RegisterRequest("Charlie Flow", "charlie.flow@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("charlie.flow@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();

        // Issue a known test token
        String rawToken = emailVerificationService.createVerificationToken(user);

        // 2. Verify email with raw token
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest(rawToken);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));

        // 3. Confirm user is verified in DB
        User verifiedUser = userRepository.findByEmail("charlie.flow@example.com").orElseThrow();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
        assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();

        // 4. Attempt login - now succeeds!
        LoginRequest loginRequest = new LoginRequest("charlie.flow@example.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.emailVerified").value(true));
    }

    @Test
    @DisplayName("verify-email rejects invalid, expired, and reused tokens with consistent 400 Bad Request")
    void testVerifyEmail_Rejections() throws Exception {
        User user = new User("David Rejection", "david.rejection@example.com", "pass");
        user = userRepository.save(user);

        // 1. Invalid / unknown token
        VerifyEmailRequest invalidReq = new VerifyEmailRequest("totally-fake-token");
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid or expired verification token"));

        // 2. Expired token
        String expiredRawToken = "expired-token-xyz-12345678901234567890";
        String expiredHash = emailVerificationService.hashToken(expiredRawToken);
        EmailVerificationToken expiredEntity = new EmailVerificationToken(
                expiredHash,
                user,
                Instant.now().minusSeconds(100)
        );
        tokenRepository.save(expiredEntity);

        VerifyEmailRequest expiredReq = new VerifyEmailRequest(expiredRawToken);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expiredReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid or expired verification token"));

        // 3. Reused token (token valid initially, then consumed)
        String reusableRawToken = emailVerificationService.createVerificationToken(user);
        VerifyEmailRequest validReq = new VerifyEmailRequest(reusableRawToken);

        // First verification succeeds
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk());

        // Second verification fails with consistent 400 Bad Request
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid or expired verification token"));
    }

    @Test
    @DisplayName("Concurrent token consumption: exactly one request succeeds and the other fails")
    void testConcurrentVerification_OnlyOneSucceeds() throws Exception {
        User user = new User("Eve Concurrent", "eve.concurrent@example.com", "pass");
        user = userRepository.save(user);

        String rawToken = emailVerificationService.createVerificationToken(user);
        VerifyEmailRequest request = new VerifyEmailRequest(rawToken);
        String requestJson = objectMapper.writeValueAsString(request);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            tasks.add(() -> {
                MvcResult result = mockMvc.perform(post("/api/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<Integer> statusCodes = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statusCodes.add(f.get());
        }

        Collections.sort(statusCodes);
        // Exactly one 200 OK and one 400 Bad Request
        assertThat(statusCodes).containsExactly(200, 400);

        // User is verified
        User verifiedUser = userRepository.findByEmail("eve.concurrent@example.com").orElseThrow();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("resend-verification returns identical 200 generic message for nonexistent, verified, and unverified users")
    void testResendVerification_GenericResponseForAllStates() throws Exception {
        // 1. Nonexistent account
        ResendVerificationRequest nonexistentReq = new ResendVerificationRequest("nonexistent@example.com");
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonexistentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

        // 2. Already verified account
        User verifiedUser = new User("Frank Verified", "frank.verified@example.com", "pass");
        verifiedUser.setEmailVerified(true);
        verifiedUser.setEmailVerifiedAt(Instant.now());
        userRepository.save(verifiedUser);

        ResendVerificationRequest verifiedReq = new ResendVerificationRequest("frank.verified@example.com");
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifiedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

        // 3. Unverified account eligible for resend
        User unverifiedUser = new User("Grace Unverified", "grace.unverified@example.com", "pass");
        unverifiedUser.setEmailVerified(false);
        userRepository.save(unverifiedUser);

        ResendVerificationRequest unverifiedReq = new ResendVerificationRequest("grace.unverified@example.com");
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unverifiedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

        // Verify only 1 email was sent (for Grace)
        verify(emailSender, times(1)).sendEmail(eq("grace.unverified@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resend-verification enforces cooldown: immediate second resend suppresses email dispatch")
    void testResendVerification_CooldownSuppressesSecondEmail() throws Exception {
        User unverifiedUser = new User("Hannah Cooldown", "hannah.cooldown@example.com", "pass");
        unverifiedUser.setEmailVerified(false);
        userRepository.save(unverifiedUser);

        ResendVerificationRequest req = new ResendVerificationRequest("hannah.cooldown@example.com");

        // First resend -> sends email
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

        // Immediate second resend -> cooldown active, suppresses email, returns identical 200 OK
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

        // Still only 1 email sent
        verify(emailSender, times(1)).sendEmail(eq("hannah.cooldown@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Registration when Mailgun is disabled or fails preserves account consistency without marking verified")
    void testRegister_MailgunFailure_PreservesConsistencyAndLeavesUserUnverified() throws Exception {
        emailVerificationProperties.setRequired(true);

        when(emailSender.sendEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.skipped("Mailgun delivery is disabled"));

        RegisterRequest request = new RegisterRequest("Ian MailgunFail", "ian.fail@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.message").value("Registration completed, but verification email could not be delivered. Please use the resend verification option to receive your link."));

        // User exists in DB and is UNVERIFIED
        User user = userRepository.findByEmail("ian.fail@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();

        // Login still blocked
        LoginRequest loginRequest = new LoginRequest("ian.fail@example.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Registration when enforcement is disabled preserves existing contract and immediate login succeeds")
    void testRegister_EnforcementDisabled_ImmediateLoginSucceeds() throws Exception {
        emailVerificationProperties.setRequired(false);

        RegisterRequest registerReq = new RegisterRequest("Jack Disabled", "jack.disabled@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        // Login succeeds immediately
        LoginRequest loginReq = new LoginRequest("jack.disabled@example.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
