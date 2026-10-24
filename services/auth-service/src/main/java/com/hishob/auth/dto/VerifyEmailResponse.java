package com.hishob.auth.dto;

public class VerifyEmailResponse {

    private boolean verified;
    private String message;

    public VerifyEmailResponse() {
    }

    public VerifyEmailResponse(boolean verified, String message) {
        this.verified = verified;
        this.message = message;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
