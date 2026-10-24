package com.hishob.auth.email;

import com.hishob.auth.email.config.MailgunProperties;
import com.hishob.auth.email.model.EmailSendResult;
import com.hishob.auth.email.service.MailgunEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * MANUAL TEST — Execute only when BOTH guards are enabled:
 * 1. Environment variable: MAILGUN_MANUAL_TEST_ENABLED=true
 * 2. JVM system property: mailgun.manual.test=true
 * 
 * Purpose: Send one real test email to an authorized Mailgun sandbox recipient.
 * 
 * Prerequisites:
 * 1. MAILGUN_ENABLED=true
 * 2. MAILGUN_API_KEY=<your sandbox API key>
 * 3. MAILGUN_DOMAIN=<your sandbox domain>
 * 4. MAILGUN_MANUAL_TEST_ENABLED=true (environment variable)
 * 5. MAILGUN_TEST_RECIPIENT=<authorized recipient email>
 * 6. mailgun.manual.test=true (JVM system property via -D flag)
 * 
 * Run:
 *   mvn test -Dtest=MailgunManualTest \
 *     -Dmailgun.manual.test=true \
 *     -DMAILGUN_MANUAL_TEST_ENABLED=true \
 *     -DMAILGUN_ENABLED=true \
 *     -DMAILGUN_API_KEY="your-sandbox-api-key" \
 *     -DMAILGUN_DOMAIN="sandboxXXX.mailgun.org" \
 *     -DMAILGUN_TEST_RECIPIENT="authorized@recipient.com"
 * 
 * Safety:
 * - No Spring context loaded (no database access)
 * - No API key printed or logged
 * - Requires explicit dual authorization
 * - Fails safely if recipient is missing
 * - Cannot run accidentally during ordinary Maven builds
 */
@EnabledIfEnvironmentVariable(named = "MAILGUN_MANUAL_TEST_ENABLED", matches = "true")
@EnabledIfSystemProperty(named = "mailgun.manual.test", matches = "true")
class MailgunManualTest {

    @Test
    void sendSingleTestEmail() {
        // Load configuration from environment variables (no Spring context)
        MailgunProperties properties = new MailgunProperties();
        properties.setEnabled(Boolean.parseBoolean(System.getenv("MAILGUN_ENABLED")));
        properties.setApiKey(System.getenv("MAILGUN_API_KEY"));
        properties.setDomain(System.getenv("MAILGUN_DOMAIN"));
        properties.setFromName("HISHOB Test");
        properties.setFromEmail(System.getenv().getOrDefault("MAILGUN_FROM_EMAIL", "noreply@hishob.com"));

        // Create email sender directly (no Spring autowiring, no database)
        MailgunEmailSender sender = new MailgunEmailSender(properties);

        // Require explicit recipient (no fallback, fail-safe behavior)
        String recipient = System.getenv("MAILGUN_TEST_RECIPIENT");
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                "MAILGUN_TEST_RECIPIENT environment variable is required. " +
                "Cannot send test email without an explicit authorized recipient."
            );
        }

        // Send one test email via existing EmailSender implementation
        EmailSendResult result = sender.sendEmail(
            recipient,
            "HISHOB Mailgun Sandbox Test",
            "This is a test email from HISHOB auth-service.\n\n" +
                "If you received this, Mailgun integration is working correctly.\n\n" +
                "Timestamp: " + java.time.Instant.now(),
            "<h2>HISHOB Mailgun Sandbox Test</h2>" +
                "<p>This is a test email from HISHOB auth-service.</p>" +
                "<p>If you received this, Mailgun integration is working correctly.</p>" +
                "<p><strong>Timestamp:</strong> " + java.time.Instant.now() + "</p>"
        );

        // Report result (never print API key or credentials)
        System.out.println("=== EMAIL SEND RESULT ===");
        System.out.println("Status: " + result.getStatus());
        System.out.println("Message ID: " + result.getMessageId());
        System.out.println("Message: " + result.getMessage());
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Failed: " + result.isFailed());
        System.out.println("Skipped: " + result.isSkipped());
        System.out.println("========================");

        // Fail the test if email was not successfully accepted by Mailgun
        if (!result.isSuccess()) {
            throw new AssertionError("Email send failed: " + result.getMessage());
        }
    }
}
