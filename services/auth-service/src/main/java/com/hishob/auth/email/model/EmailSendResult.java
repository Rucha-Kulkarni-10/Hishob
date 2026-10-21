package com.hishob.auth.email.model;

/**
 * Result object returned by EmailSender operations.
 *
 * Encapsulates status, provider message ID, and sanitized descriptive messages
 * without exposing internal secrets, tokens, or credentials.
 */
public class EmailSendResult {

    private final EmailSendStatus status;
    private final String messageId;
    private final String message;

    private EmailSendResult(EmailSendStatus status, String messageId, String message) {
        this.status = status;
        this.messageId = messageId;
        this.message = message;
    }

    public static EmailSendResult success(String messageId, String message) {
        return new EmailSendResult(EmailSendStatus.SUCCESS, messageId, message);
    }

    public static EmailSendResult skipped(String reason) {
        return new EmailSendResult(EmailSendStatus.SKIPPED_DISABLED, null, reason);
    }

    public static EmailSendResult configurationError(String reason) {
        return new EmailSendResult(EmailSendStatus.CONFIGURATION_ERROR, null, reason);
    }

    public static EmailSendResult providerError(String errorDetail) {
        return new EmailSendResult(EmailSendStatus.PROVIDER_ERROR, null, errorDetail);
    }

    public static EmailSendResult timeout(String errorDetail) {
        return new EmailSendResult(EmailSendStatus.TIMEOUT, null, errorDetail);
    }

    public static EmailSendResult rateLimited(String errorDetail) {
        return new EmailSendResult(EmailSendStatus.RATE_LIMITED, null, errorDetail);
    }

    public EmailSendStatus getStatus() {
        return status;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return status == EmailSendStatus.SUCCESS;
    }

    public boolean isSkipped() {
        return status == EmailSendStatus.SKIPPED_DISABLED;
    }

    public boolean isFailed() {
        return status != EmailSendStatus.SUCCESS && status != EmailSendStatus.SKIPPED_DISABLED;
    }

    @Override
    public String toString() {
        return "EmailSendResult{" +
                "status=" + status +
                ", messageId='" + messageId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
