package com.hishob.expense.client;

import com.hishob.expense.exception.InvalidGroupException;
import com.hishob.expense.exception.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GroupServiceClientTest {

    private static final String TOKEN = "test-service-token";

    private MockRestServiceServer mockServer;
    private GroupServiceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.builder(restTemplate).baseUrl("http://localhost:8082").build();
        client = new GroupServiceClient(restClient, TOKEN);
    }

    @Test
    @DisplayName("validateGroupExists — sends X-Internal-Token header and succeeds on 204")
    void testValidateGroupExists_SendsToken_Success() {
        UUID groupId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8082/internal/groups/" + groupId + "/exists"))
                .andExpect(header(GroupServiceClient.INTERNAL_TOKEN_HEADER, TOKEN))
                .andRespond(withNoContent());

        assertThatCode(() -> client.validateGroupExists(groupId)).doesNotThrowAnyException();
        mockServer.verify();
    }

    @Test
    @DisplayName("validateGroupExists — 404 throws InvalidGroupException")
    void testValidateGroupExists_NotFound_Throws() {
        UUID groupId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8082/internal/groups/" + groupId + "/exists"))
                .andExpect(header(GroupServiceClient.INTERNAL_TOKEN_HEADER, TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.validateGroupExists(groupId))
                .isInstanceOf(InvalidGroupException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("validateGroupExists — 401 (wrong/missing token rejected by server) throws InvalidGroupException")
    void testValidateGroupExists_Unauthorized_Throws() {
        UUID groupId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8082/internal/groups/" + groupId + "/exists"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.validateGroupExists(groupId))
                .isInstanceOf(InvalidGroupException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("validateMember — sends X-Internal-Token header and succeeds on 204")
    void testValidateMember_SendsToken_Success() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8082/internal/groups/" + groupId + "/members/" + userId + "/exists"))
                .andExpect(header(GroupServiceClient.INTERNAL_TOKEN_HEADER, TOKEN))
                .andRespond(withNoContent());

        assertThatCode(() -> client.validateMember(groupId, userId, "Creator")).doesNotThrowAnyException();
        mockServer.verify();
    }

    @Test
    @DisplayName("validateMember — 404 throws PermissionDeniedException")
    void testValidateMember_NotFound_Throws() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8082/internal/groups/" + groupId + "/members/" + userId + "/exists"))
                .andExpect(header(GroupServiceClient.INTERNAL_TOKEN_HEADER, TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.validateMember(groupId, userId, "Creator"))
                .isInstanceOf(PermissionDeniedException.class);
        mockServer.verify();
    }
}
