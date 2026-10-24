package com.hishob.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for email verification.
 *
 * Properties:
 * - auth.email-verification.required: whether email verification is strictly enforced (default false)
 * - auth.email-verification.token-validity-seconds: token expiry duration in seconds (default 86400 / 24 hours)
 * - auth.email-verification.resend-cooldown-seconds: minimum cooldown between resend requests (default 60 seconds)
 * - auth.email-verification.frontend-url: base URL for frontend verification link (default http://localhost:3000)
 * - auth.email-verification.verification-path: path on frontend for email verification (default /verify-email)
 */
@Component
@ConfigurationProperties(prefix = "auth.email-verification")
public class EmailVerificationProperties {

    private boolean required = false;
    private long tokenValiditySeconds = 86400; // 24 hours
    private long resendCooldownSeconds = 60;   // 60 seconds
    private String frontendUrl = "http://localhost:3000";
    private String verificationPath = "/verify-email";

    public EmailVerificationProperties() {
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public long getTokenValiditySeconds() {
        return tokenValiditySeconds;
    }

    public void setTokenValiditySeconds(long tokenValiditySeconds) {
        this.tokenValiditySeconds = tokenValiditySeconds;
    }

    public long getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(long resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:3000";
    }

    public String getVerificationPath() {
        return verificationPath;
    }

    public void setVerificationPath(String verificationPath) {
        this.verificationPath = verificationPath != null ? verificationPath.trim() : "/verify-email";
    }
}
