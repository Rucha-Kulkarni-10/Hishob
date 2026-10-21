package com.hishob.auth.email.service;

import com.hishob.auth.email.model.EmailSendResult;

/**
 * Abstraction for sending outbound emails.
 */
public interface EmailSender {

    /**
     * Sends an email to a recipient with subject, plain text body, and optional HTML body.
     *
     * @param to recipient email address
     * @param subject email subject line
     * @param textBody plain-text fallback content
     * @param htmlBody optional rich HTML content (can be null or blank)
     * @return EmailSendResult containing delivery outcome and sanitized status
     */
    EmailSendResult sendEmail(String to, String subject, String textBody, String htmlBody);

    /**
     * Sends a plain-text email to a recipient.
     *
     * @param to recipient email address
     * @param subject email subject line
     * @param textBody plain-text content
     * @return EmailSendResult containing delivery outcome and sanitized status
     */
    default EmailSendResult sendEmail(String to, String subject, String textBody) {
        return sendEmail(to, subject, textBody, null);
    }
}
