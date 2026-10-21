package com.hishob.expense.client;

import com.hishob.expense.dto.CreateNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationServiceClientTargetTest {

    private static final String BASE_URL = "http://auth-service.test:8081";
    private static final String TOKEN = "test-internal-token";

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private NotificationServiceClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new NotificationServiceClient(builder.build(), TOKEN);
    }

    @Test
    @DisplayName("NotificationServiceClient binds to hishob.auth-service properties, not notification-service")
    void testConstructorPropertyTargetsAuthService() {
        Constructor<?> constructor = publicStringConstructor();
        String[] values = valueAnnotations(constructor);

        assertThat(values).hasSize(2);
        assertThat(values).allMatch(value -> value.contains("hishob.auth-service."));
        assertThat(values).noneMatch(value -> value.contains("hishob.notification-service"));
        assertThat(values[0]).isEqualTo("${hishob.auth-service.base-url:http://localhost:8081}");
        assertThat(values[1]).isEqualTo("${hishob.auth-service.internal-token}");
    }

    @Test
    @DisplayName("createNotification POSTs the existing contract body to /internal/notifications on Auth Service")
    void testCreateNotification_RequestsAuthInternalPathWithCompatibleContract() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String expenseId = UUID.randomUUID().toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("expenseId", expenseId);
        CreateNotificationRequest request =
                new CreateNotificationRequest(userId, groupId, "EXPENSE_ADDED", "Expense added", metadata);

        mockServer.expect(requestTo(BASE_URL + "/internal/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json(
                        "{\"userId\":\"" + userId + "\",\"groupId\":\"" + groupId + "\","
                                + "\"type\":\"EXPENSE_ADDED\",\"message\":\"Expense added\","
                                + "\"metadata\":{\"expenseId\":\"" + expenseId + "\"}}"))
                .andRespond(withSuccess());

        client.createNotification(request);

        mockServer.verify();
    }

    @Test
    @DisplayName("createNotification swallows server errors so the main operation still succeeds")
    void testCreateNotification_ServerErrorIsSwallowed() {
        CreateNotificationRequest request = new CreateNotificationRequest(
                UUID.randomUUID(), null, "SETTLEMENT_RECORDED", "Settlement recorded", null);

        mockServer.expect(requestTo(BASE_URL + "/internal/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withServerError());

        assertThatCode(() -> client.createNotification(request)).doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    @DisplayName("Active production configuration does not target a separately deployed Notification Service")
    void testMainApplicationYmlHasNoNotificationServiceTarget() throws Exception {
        String mainYml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(mainYml)
                .doesNotContain("notification-service")
                .doesNotContain("NOTIFICATION_SERVICE_URL");
    }

    private Constructor<?> publicStringConstructor() {
        for (Constructor<?> candidate : NotificationServiceClient.class.getConstructors()) {
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length == 2 && types[0] == String.class && types[1] == String.class) {
                return candidate;
            }
        }
        throw new AssertionError("No public (String, String) constructor on NotificationServiceClient");
    }

    private String[] valueAnnotations(Constructor<?> constructor) {
        Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
        String[] values = new String[parameterAnnotations.length];
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof Value value) {
                    values[i] = value.value();
                }
            }
        }
        return values;
    }
}
