package com.hishob.auth.dto;

public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private UserResponse user;
    private boolean emailVerificationRequired;
    private String message;

    public AuthResponse() {
        this.tokenType = "Bearer";
    }

    public AuthResponse(String accessToken, UserResponse user) {
        this.accessToken = accessToken;
        this.tokenType = accessToken != null ? "Bearer" : null;
        this.user = user;
        this.emailVerificationRequired = false;
    }

    public AuthResponse(String accessToken, String tokenType, UserResponse user) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.user = user;
        this.emailVerificationRequired = false;
    }

    public static AuthResponse authenticated(String accessToken, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", user);
    }

    public static AuthResponse verificationRequired(UserResponse user, String message) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(null);
        response.setTokenType(null);
        response.setUser(user);
        response.setEmailVerificationRequired(true);
        response.setMessage(message);
        return response;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequired;
    }

    public void setEmailVerificationRequired(boolean emailVerificationRequired) {
        this.emailVerificationRequired = emailVerificationRequired;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
