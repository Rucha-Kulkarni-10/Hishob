package com.hishob.auth.email.model;

/**
 * Status of an email dispatch operation.
 */
public enum EmailSendStatus {
    /**
     * Email accepted by Mailgun API (HTTP 200).
     */
    SUCCESS,

    /**
     * Email dispatch was skipped because email delivery is disabled in configuration.
     */
    SKIPPED_DISABLED,

    /**
     * Email dispatch could not proceed due to invalid or missing configuration or invalid arguments.
     */
    CONFIGURATION_ERROR,

    /**
     * Mailgun returned an error (HTTP 4xx or 5xx) indicating rejection or server failure.
     */
    PROVIDER_ERROR,

    /**
     * Request to Mailgun timed out or failed due to network/connectivity issues.
     */
    TIMEOUT,

    /**
     * Mailgun rejected the request due to exceeding rate limits (HTTP 429).
     */
    RATE_LIMITED
}
