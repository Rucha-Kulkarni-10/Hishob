package com.hishob.group.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * B-08: /api/auth/users/{id}/exists stopped being a public oracle, so the only legitimate caller
 * has to present service credentials. This proves Group Service does, and keeps its 404 handling.
 */
class AuthServiceClientExistsCallTest {

    private static final String BASE_URL = "http://auth-service.test:8081";
    private static final String TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private MockRestServiceServer mockServer;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new AuthServiceClient(builder.build(), TOKEN);
    }

    @Test
    @DisplayName("validateUserExists sends the internal token on the exists lookup")
    void validateUserExists_SendsInternalToken() {
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/auth/users/" + userId + "/exists"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(INTERNAL_TOKEN_HEADER, TOKEN))
                .andRespond(withSuccess());

        client.validateUserExists(userId);

        mockServer.verify();
    }

    @Test
    @DisplayName("A 404 from the exists lookup still surfaces as an invalid target user")
    void validateUserExists_KeepsNotFoundHandling() {
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/auth/users/" + userId + "/exists"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(INTERNAL_TOKEN_HEADER, TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.validateUserExists(userId))
                .hasMessageContaining("Target user does not exist");
    }
}
