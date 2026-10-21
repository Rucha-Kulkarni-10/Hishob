package com.hishob.auth.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Mailgun email delivery.
 *
 * Properties are mapped from the "mailgun" prefix:
 * - mailgun.enabled: whether email delivery is enabled (default false)
 * - mailgun.api-key: Mailgun API key (no default secret)
 * - mailgun.domain: Mailgun sending domain
 * - mailgun.base-url: Mailgun API base URL (default https://api.mailgun.net)
 * - mailgun.from-name: Sender display name (default HISHOB)
 * - mailgun.from-email: Sender email address (default noreply@hishob.com)
 * - mailgun.connect-timeout-ms: Connection timeout in milliseconds (default 5000)
 * - mailgun.read-timeout-ms: Response read timeout in milliseconds (default 10000)
 */
@Component
@ConfigurationProperties(prefix = "mailgun")
public class MailgunProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String domain = "";
    private String baseUrl = "https://api.mailgun.net";
    private String fromName = "HISHOB";
    private String fromEmail = "noreply@hishob.com";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

    public MailgunProperties() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain != null ? domain.trim() : "";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://api.mailgun.net";
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName != null ? fromName.trim() : "HISHOB";
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "noreply@hishob.com";
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Checks if the configuration has all required parameters for sending emails.
     */
    public boolean hasValidSendingConfig() {
        return enabled
                && apiKey != null && !apiKey.isBlank()
                && domain != null && !domain.isBlank()
                && baseUrl != null && !baseUrl.isBlank();
    }
}
