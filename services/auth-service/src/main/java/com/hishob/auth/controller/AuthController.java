package com.hishob.auth.controller;

import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.LoginRequest;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.dto.ResendVerificationRequest;
import com.hishob.auth.dto.ResendVerificationResponse;
import com.hishob.auth.dto.UserResponse;
import com.hishob.auth.dto.UserSummaryResponse;
import com.hishob.auth.dto.VerifyEmailRequest;
import com.hishob.auth.dto.VerifyEmailResponse;
import com.hishob.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        VerifyEmailResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ResendVerificationResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        ResendVerificationResponse response = authService.resendVerification(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/exists")
    public ResponseEntity<Void> checkUserExists(@PathVariable UUID userId) {
        if (authService.userExistsById(userId)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserSummaryResponse> getUserById(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserSummaryResponse response = authService.getUserSummaryById(userId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/batch")
    public ResponseEntity<List<UserSummaryResponse>> getUsersBatch(@RequestBody List<UUID> userIds) {
        List<UserSummaryResponse> response = authService.getUsersSummaryByIds(userIds);
        return ResponseEntity.ok(response);
    }

}
