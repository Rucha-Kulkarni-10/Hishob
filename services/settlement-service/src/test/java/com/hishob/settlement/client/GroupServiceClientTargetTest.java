package com.hishob.settlement.client;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroupServiceClientTargetTest {

    private static final String BASE_URL = "http://expense-service.test:8083";
    private static final String TOKEN = "hishob-test-internal-token-for-automated-tests-only";

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private GroupServiceClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GroupServiceClient(builder.build(), TOKEN);
    }

    @Test
    @DisplayName("GroupServiceClient binds to hishob.expense-service properties, not group-service")
    void testConstructorPropertyTargetsExpenseService() {
        Constructor<?> constructor = publicStringConstructor();
        String[] values = valueAnnotations(constructor);

        assertThat(values).hasSize(2);
        assertThat(values).allMatch(value -> value.contains("hishob.expense-service."));
        assertThat(values).noneMatch(value -> value.contains("hishob.group-service"));
        assertThat(values[0]).isEqualTo("${hishob.expense-service.base-url:http://localhost:8083}");
    }

    @Test
    @DisplayName("validateGroupExists calls /internal/groups/{id}/exists on Expense Service")
    void testValidateGroupExists_RequestsExpenseServiceInternalPath() {
        UUID groupId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/groups/" + groupId + "/exists"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.validateGroupExists(groupId);

        mockServer.verify();
    }

    @Test
    @DisplayName("validateMember calls membership existence on Expense Service")
    void testValidateMember_RequestsExpenseServiceInternalPath() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/groups/" + groupId
                        + "/members/" + userId + "/exists"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.validateMember(groupId, userId);

        mockServer.verify();
    }

    @Test
    @DisplayName("validateRecipientMember calls membership existence on Expense Service")
    void testValidateRecipientMember_RequestsExpenseServiceInternalPath() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/groups/" + groupId
                        + "/members/" + userId + "/exists"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.validateRecipientMember(groupId, userId);

        mockServer.verify();
    }

    @Test
    @DisplayName("getGroupMemberIds returns member ids from Expense Service")
    void testGetGroupMemberIds_RequestsExpenseServiceInternalPath() {
        UUID groupId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/groups/" + groupId + "/members"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[\"" + first + "\",\"" + second + "\"]"));

        List<UUID> members = client.getGroupMemberIds(groupId);

        mockServer.verify();
        assertThat(members).containsExactly(first, second);
    }

    private Constructor<?> publicStringConstructor() {
        for (Constructor<?> candidate : GroupServiceClient.class.getConstructors()) {
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length == 2 && types[0] == String.class && types[1] == String.class) {
                return candidate;
            }
        }
        throw new AssertionError("No public (String, String) constructor on GroupServiceClient");
    }

    private String[] valueAnnotations(Constructor<?> constructor) {
        Annotation[][] annotations = constructor.getParameterAnnotations();
        String[] values = new String[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation instanceof Value value) {
                    values[i] = value.value();
                }
            }
        }
        return values;
    }
}
