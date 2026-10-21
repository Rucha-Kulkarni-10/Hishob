package com.hishob.auth.email.service;

import com.hishob.auth.email.config.MailgunProperties;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.email.model.MailgunApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Implementation of EmailSender using the Mailgun REST API.
 *
 * Dispatches HTTP requests to Mailgun's messages endpoint:
 *   POST {baseUrl}/v3/{domain}/messages
 *
 * Features:
 * - HTTP Basic Authentication using username "api" and configured API key
 * - Form-encoded (application/x-www-form-urlencoded) payload
 * - Explicit connection and read timeouts
 * - Safe error handling: provider rejections, auth failures, 429 rate limits, 5xx outages, and timeouts
 * - Masked recipient logging to preserve user privacy
 * - Complete isolation: disabled state performs zero network calls and returns an explicit skipped result
 */
@Service
public class MailgunEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(MailgunEmailSender.class);

    private final MailgunProperties properties;
    private final RestClient restClient;

    @Autowired
    public MailgunEmailSender(MailgunProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Constructor for unit testing with a pre-configured or mocked RestClient.
     */
    public MailgunEmailSender(MailgunProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public EmailSendResult sendEmail(String to, String subject, String textBody, String htmlBody) {
        if (!properties.isEnabled()) {
            log.debug("Mailgun email delivery is disabled. Skipping email to [{}]", maskEmail(to));
            return EmailSendResult.skipped("Email delivery is disabled");
        }

        if (to == null || to.isBlank()) {
            log.warn("Cannot send email: recipient address is empty or null");
            return EmailSendResult.configurationError("Recipient email address is required");
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getDomain() == null || properties.getDomain().isBlank()) {
            log.warn("Mailgun email delivery is enabled, but required configuration (api-key or domain) is missing. Skipping email to [{}]", maskEmail(to));
            return EmailSendResult.configurationError("Mailgun is enabled but missing required configuration (api-key or domain)");
        }

        String uri = buildMessagesUri();
        MultiValueMap<String, String> formData = buildFormData(to, subject, textBody, htmlBody);

        try {
            MailgunApiResponse response = restClient.post()
                    .uri(uri)
                    .headers(headers -> headers.setBasicAuth("api", properties.getApiKey()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(MailgunApiResponse.class);

            String messageId = response != null ? response.getId() : null;
            String message = response != null ? response.getMessage() : "Accepted";

            log.info("Email successfully accepted by Mailgun for recipient [{}], messageId: {}", maskEmail(to), messageId);
            return EmailSendResult.success(messageId, message);

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Mailgun authentication failed (HTTP 401). Verify configured API key.");
            return EmailSendResult.providerError("Mailgun authentication failed: HTTP 401 Unauthorized");

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Mailgun rate limit reached (HTTP 429) for recipient [{}]", maskEmail(to));
            return EmailSendResult.rateLimited("Mailgun rate limit reached: HTTP 429");

        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Mailgun rejected email request (HTTP 400) for recipient [{}]", maskEmail(to));
            return EmailSendResult.providerError("Mailgun rejected request: HTTP 400 Bad Request");

        } catch (HttpClientErrorException e) {
            log.warn("Mailgun client error (HTTP {}) for recipient [{}]", e.getStatusCode().value(), maskEmail(to));
            return EmailSendResult.providerError("Mailgun client error: HTTP " + e.getStatusCode().value());

        } catch (HttpServerErrorException e) {
            log.warn("Mailgun server error (HTTP {}) for recipient [{}]", e.getStatusCode().value(), maskEmail(to));
            return EmailSendResult.providerError("Mailgun server error: HTTP " + e.getStatusCode().value());

        } catch (ResourceAccessException e) {
            log.warn("Mailgun network or timeout error for recipient [{}]: {}", maskEmail(to), e.getMessage());
            return EmailSendResult.timeout("Mailgun network error: " + e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error dispatching email via Mailgun for recipient [{}]: {}", maskEmail(to), e.getMessage());
            return EmailSendResult.providerError("Unexpected error sending email: " + e.getMessage());
        }
    }

    private String buildMessagesUri() {
        String baseUrl = properties.getBaseUrl() != null ? properties.getBaseUrl().replaceAll("/+$", "") : "https://api.mailgun.net";
        String domain = properties.getDomain() != null ? properties.getDomain().trim() : "";
        return baseUrl + "/v3/" + domain + "/messages";
    }

    private MultiValueMap<String, String> buildFormData(String to, String subject, String textBody, String htmlBody) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("from", formatFromHeader());
        formData.add("to", to.trim());
        formData.add("subject", subject != null ? subject : "");
        formData.add("text", textBody != null ? textBody : "");

        if (htmlBody != null && !htmlBody.isBlank()) {
            formData.add("html", htmlBody);
        }

        return formData;
    }

    private String formatFromHeader() {
        String fromName = properties.getFromName() != null ? properties.getFromName().trim() : "";
        String fromEmail = properties.getFromEmail() != null && !properties.getFromEmail().isBlank()
                ? properties.getFromEmail().trim()
                : "noreply@" + properties.getDomain();

        if (!fromName.isBlank()) {
            return fromName + " <" + fromEmail + ">";
        }
        return fromEmail;
    }

    /**
     * Masks recipient email for logging to protect user privacy (e.g. j***e@example.com).
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? trimmed.substring(atIndex) : "");
        }
        String local = trimmed.substring(0, atIndex);
        String domain = trimmed.substring(atIndex);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
