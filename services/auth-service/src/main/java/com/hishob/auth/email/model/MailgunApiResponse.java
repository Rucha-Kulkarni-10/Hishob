package com.hishob.auth.email.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mailgun API response payload representation for HTTP 200 responses.
 *
 * Example Mailgun response:
 * {
 *   "id": "<20261016...mailgun.org>",
 *   "message": "Queued. Thank you."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailgunApiResponse {

    private String id;
    private String message;

    public MailgunApiResponse() {
    }

    public MailgunApiResponse(String id, String message) {
        this.id = id;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
