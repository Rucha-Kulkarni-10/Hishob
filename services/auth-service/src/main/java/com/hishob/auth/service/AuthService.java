package com.hishob.auth.service;

import com.hishob.auth.config.EmailVerificationProperties;
import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.LoginRequest;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.dto.ResendVerificationRequest;
import com.hishob.auth.dto.ResendVerificationResponse;
import com.hishob.auth.dto.UserResponse;
import com.hishob.auth.dto.UserSummaryResponse;
import com.hishob.auth.dto.VerifyEmailRequest;
import com.hishob.auth.dto.VerifyEmailResponse;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.entity.FriendshipStatus;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.DuplicateEmailException;
import com.hishob.auth.exception.EmailNotVerifiedException;
import com.hishob.auth.exception.InvalidCredentialsException;
import com.hishob.auth.exception.ResourceNotFoundException;
import com.hishob.auth.repository.FriendshipRepository;
import com.hishob.auth.repository.UserRepository;
import com.hishob.auth.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    /**
     * B-09: one message for both "no such user" and "you may not see this user", so a guessed UUID
     * cannot be confirmed or denied through {@code GET /api/auth/users/{userId}}.
     */
    private static final String USER_SUMMARY_ABSENT = "User not found with id: ";

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationProperties emailVerificationProperties;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            FriendshipRepository friendshipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailVerificationService emailVerificationService,
            EmailVerificationProperties emailVerificationProperties) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.emailVerificationProperties = emailVerificationProperties != null ? emailVerificationProperties : new EmailVerificationProperties();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            // Must not echo the submitted or stored address: the response body is public.
            throw new DuplicateEmailException("Registration failed because the account already exists.");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);

        if (emailVerificationProperties.isRequired() && emailVerificationService != null) {
            String rawToken = emailVerificationService.createVerificationToken(savedUser);
            EmailSendResult sendResult = emailVerificationService.sendVerificationEmail(savedUser, rawToken);

            String message;
            if (sendResult.isFailed() || sendResult.isSkipped()) {
                message = "Registration completed, but verification email could not be delivered. Please use the resend verification option to receive your link.";
            } else {
                message = "Registration successful. Please check your email to verify your account.";
            }

            return AuthResponse.verificationRequired(UserResponse.fromEntity(savedUser), message);
        }

        String token = jwtService.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName()
        );

        return new AuthResponse(token, UserResponse.fromEntity(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (emailVerificationProperties.isRequired() && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email is not verified. Please verify your email before logging in.");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName()
        );

        return new AuthResponse(token, UserResponse.fromEntity(user));
    }

    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        if (emailVerificationService == null) {
            throw new IllegalStateException("Email verification service is not available");
        }
        return emailVerificationService.verifyEmail(request.getToken());
    }

    public ResendVerificationResponse resendVerification(ResendVerificationRequest request) {
        if (emailVerificationService == null) {
            return new ResendVerificationResponse(EmailVerificationService.GENERIC_RESEND_MESSAGE);
        }
        return emailVerificationService.resendVerification(request.getEmail());
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public boolean userExistsById(UUID userId) {
        return userRepository.existsById(userId);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getUserSummaryById(UUID userId, String viewerEmail) {
        User viewer = userRepository.findByEmail(viewerEmail.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(USER_SUMMARY_ABSENT + userId));

        boolean isSelf = viewer.getId().equals(userId);
        boolean isAcceptedFriend = !isSelf
                && friendshipRepository.countBetween(viewer.getId(), userId, FriendshipStatus.ACCEPTED) > 0;

        if (!isSelf && !isAcceptedFriend) {
            throw new ResourceNotFoundException(USER_SUMMARY_ABSENT + userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_SUMMARY_ABSENT + userId));

        return UserSummaryResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getUsersSummaryByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userRepository.findAllByIdIn(userIds).stream()
                .map(UserSummaryResponse::fromEntity)
                .collect(Collectors.toList());
    }

}
