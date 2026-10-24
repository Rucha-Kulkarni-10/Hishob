package com.hishob.auth.email;

import com.hishob.auth.email.config.MailgunProperties;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.email.model.EmailSendStatus;
import com.hishob.auth.email.service.MailgunEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MailgunEmailSenderTest {

    private MailgunProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private MailgunEmailSender emailSender;

    private static final String TEST_API_KEY = "test-key-abc123xyz";
    private static final String TEST_DOMAIN = "sandbox123.mailgun.org";
    private static final String TEST_BASE_URL = "http://localhost:8089";

    @BeforeEach
    void setUp() {
        properties = new MailgunProperties();
        properties.setEnabled(true);
        properties.setApiKey(TEST_API_KEY);
        properties.setDomain(TEST_DOMAIN);
        properties.setBaseUrl(TEST_BASE_URL);
        properties.setFromName("HISHOB Test");
        properties.setFromEmail("noreply@hishob.com");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(3000);

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        emailSender = new MailgunEmailSender(properties, restClientBuilder.build());
    }

    @Test
    @DisplayName("sendEmail - When disabled, makes 0 HTTP calls and returns SKIPPED_DISABLED")
    void testSendEmail_WhenDisabled_DoesNotMakeHttpRequestAndReturnsSkipped() {
        properties.setEnabled(false);

        EmailSendResult result = emailSender.sendEmail("user@example.com", "Test Subject", "Test Body");

        assertNotNull(result);
        assertEquals(EmailSendStatus.SKIPPED_DISABLED, result.getStatus());
        assertTrue(result.isSkipped());
        assertFalse(result.isSuccess());
        assertFalse(result.isFailed());
        assertNull(result.getMessageId());

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - When enabled but missing apiKey, returns CONFIGURATION_ERROR")
    void testSendEmail_WhenEnabledMissingApiKey_ReturnsConfigurationError() {
        properties.setApiKey("");

        EmailSendResult result = emailSender.sendEmail("user@example.com", "Test Subject", "Test Body");

        assertNotNull(result);
        assertEquals(EmailSendStatus.CONFIGURATION_ERROR, result.getStatus());
        assertTrue(result.isFailed());

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - When enabled but missing domain, returns CONFIGURATION_ERROR")
    void testSendEmail_WhenEnabledMissingDomain_ReturnsConfigurationError() {
        properties.setDomain("");

        EmailSendResult result = emailSender.sendEmail("user@example.com", "Test Subject", "Test Body");

        assertNotNull(result);
        assertEquals(EmailSendStatus.CONFIGURATION_ERROR, result.getStatus());
        assertTrue(result.isFailed());

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - When recipient is null or blank, returns CONFIGURATION_ERROR")
    void testSendEmail_WhenRecipientBlank_ReturnsConfigurationError() {
        EmailSendResult resultNull = emailSender.sendEmail(null, "Subject", "Body");
        assertEquals(EmailSendStatus.CONFIGURATION_ERROR, resultNull.getStatus());

        EmailSendResult resultBlank = emailSender.sendEmail("   ", "Subject", "Body");
        assertEquals(EmailSendStatus.CONFIGURATION_ERROR, resultBlank.getStatus());

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Successful dispatch with plain text and HTML")
    void testSendEmail_SuccessWithPlainTextAndHtml() {
        String expectedBasicAuth = "Basic " + Base64.getEncoder().encodeToString(
                ("api:" + TEST_API_KEY).getBytes(StandardCharsets.UTF_8));
        String expectedUri = TEST_BASE_URL + "/v3/" + TEST_DOMAIN + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicAuth))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("to=recipient%40example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject=Welcome+to+HISHOB")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("text=Plain+text+content")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("html=%3Cp%3EHTML+content%3C%2Fp%3E")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("from=HISHOB+Test+%3Cnoreply%40hishob.com%3E")))
                .andRespond(withSuccess(
                        "{\"id\": \"<20261016183000.abc12345@" + TEST_DOMAIN + ">\", \"message\": \"Queued. Thank you.\"}",
                        MediaType.APPLICATION_JSON));

        EmailSendResult result = emailSender.sendEmail(
                "recipient@example.com",
                "Welcome to HISHOB",
                "Plain text content",
                "<p>HTML content</p>"
        );

        assertNotNull(result);
        assertEquals(EmailSendStatus.SUCCESS, result.getStatus());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailed());
        assertEquals("<20261016183000.abc12345@" + TEST_DOMAIN + ">", result.getMessageId());
        assertEquals("Queued. Thank you.", result.getMessage());

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Successful dispatch with plain text only")
    void testSendEmail_SuccessPlainTextOnly() {
        String expectedUri = TEST_BASE_URL + "/v3/" + TEST_DOMAIN + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("html="))))
                .andRespond(withSuccess(
                        "{\"id\": \"<plain-msg-id@" + TEST_DOMAIN + ">\", \"message\": \"Queued. Thank you.\"}",
                        MediaType.APPLICATION_JSON));

        EmailSendResult result = emailSender.sendEmail(
                "recipient@example.com",
                "Text Only Subject",
                "Plain text only body"
        );

        assertNotNull(result);
        assertEquals(EmailSendStatus.SUCCESS, result.getStatus());
        assertTrue(result.isSuccess());
        assertEquals("<plain-msg-id@" + TEST_DOMAIN + ">", result.getMessageId());

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Provider rejection HTTP 400 Bad Request")
    void testSendEmail_ProviderRejection_Http400() {
        String expectedUri = TEST_BASE_URL + "/v3/" + TEST_DOMAIN + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"message\": \"'to' parameter is not an authorized recipient\"}"));

        EmailSendResult result = emailSender.sendEmail(
                "unauthorized@example.com",
                "Subject",
                "Body"
        );

        assertNotNull(result);
        assertEquals(EmailSendStatus.PROVIDER_ERROR, result.getStatus());
        assertTrue(result.isFailed());
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("HTTP 400"));

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Authentication failure HTTP 401 Unauthorized")
    void testSendEmail_AuthenticationFailure_Http401() {
        String expectedUri = TEST_BASE_URL + "/v3/" + TEST_DOMAIN + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest().body("{\"message\": \"Forbidden\"}"));

        EmailSendResult result = emailSender.sendEmail(
                "user@example.com",
                "Subject",
                "Body"
        );

        assertNotNull(result);
        assertEquals(EmailSendStatus.PROVIDER_ERROR, result.getStatus());
        assertTrue(result.isFailed());
        assertTrue(result.getMessage().contains("HTTP 401"));

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Rate limited HTTP 429 Too Many Requests")
    void testSendEmail_RateLimited_Http429() {
        String expectedUri = TEST_BASE_URL + "/v3/" + TEST_DOMAIN + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\": \"Too Many Requests\"}"));

        EmailSendResult result = emailSender.sendEmail(
                "user@example.com",
                "Subject",
                "Body"
        );

        assertNotNull(result);
        assertEquals(EmailSendStatus.RATE_LIMITED, result.getStatus());
        assertTrue(result.isFailed());
        assertTrue(result.getMessage().contains("HTTP 429"));

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Provider outage HTTP 500 Internal Server Error")
    void testSendEmail_ProviderOutage_Http500() {
        String expectedUri = TEST_BASE_URL + "/v3/" + TEST_DOMAIN + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("{\"message\": \"Internal Server Error\"}"));

        EmailSendResult result = emailSender.sendEmail(
                "user@example.com",
                "Subject",
                "Body"
        );

        assertNotNull(result);
        assertEquals(EmailSendStatus.PROVIDER_ERROR, result.getStatus());
        assertTrue(result.isFailed());
        assertTrue(result.getMessage().contains("HTTP 500"));

        mockServer.verify();
    }

    @Test
    @DisplayName("sendEmail - Network failure or timeout returns TIMEOUT status")
    void testSendEmail_NetworkTimeout() {
        // Create an EmailSender with a RestClient whose requestFactory throws ResourceAccessException
        RestClient mockRestClient = RestClient.builder()
                .requestFactory((uri, httpMethod) -> {
                    throw new ResourceAccessException("Connection timed out");
                })
                .build();

        MailgunEmailSender timeoutSender = new MailgunEmailSender(properties, mockRestClient);

        EmailSendResult result = timeoutSender.sendEmail("user@example.com", "Subject", "Body");

        assertNotNull(result);
        assertEquals(EmailSendStatus.TIMEOUT, result.getStatus());
        assertTrue(result.isFailed());
        assertTrue(result.getMessage().contains("Connection timed out"));
    }

    @Test
    @DisplayName("maskEmail - Protects recipient privacy in logs")
    void testMaskEmail() {
        assertEquals("***", MailgunEmailSender.maskEmail(null));
        assertEquals("***", MailgunEmailSender.maskEmail(""));
        assertEquals("***@example.com", MailgunEmailSender.maskEmail("a@example.com"));
        assertEquals("a***@example.com", MailgunEmailSender.maskEmail("ab@example.com"));
        assertEquals("t***t@example.com", MailgunEmailSender.maskEmail("test@example.com"));
        assertEquals("p***a@domain.org", MailgunEmailSender.maskEmail("prasanna@domain.org"));
    }
}
