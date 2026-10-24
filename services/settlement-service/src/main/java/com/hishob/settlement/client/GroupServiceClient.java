package com.hishob.settlement.client;

import com.hishob.settlement.exception.GroupNotFoundException;
import com.hishob.settlement.exception.InvalidRecipientException;
import com.hishob.settlement.exception.PermissionDeniedException;
import com.hishob.settlement.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class GroupServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public GroupServiceClient(
            @Value("${hishob.expense-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${hishob.expense-service.internal-token}") String internalToken) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.internalToken = internalToken;
    }

    GroupServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public void validateGroupExists(UUID groupId) {
        try {
            restClient.get()
                    .uri("/internal/groups/{groupId}/exists", groupId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound e) {
            throw new GroupNotFoundException(
                    "Group not found: " + groupId);

        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException(
                    "Unable to validate group: " + groupId);

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Group Service unavailable");
        }
    }

    public void validateMember(UUID groupId, UUID userId) {
        try {
            restClient.get()
                    .uri(
                            "/internal/groups/{groupId}/members/{userId}/exists",
                            groupId,
                            userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound e) {
            throw new PermissionDeniedException(
                    "User is not a member of this group");

        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException(
                    "Unable to validate membership");

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Group Service unavailable");
        }
    }

    public void validateRecipientMember(UUID groupId, UUID userId) {
        try {
            restClient.get()
                    .uri(
                            "/internal/groups/{groupId}/members/{userId}/exists",
                            groupId,
                            userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidRecipientException(
                    "Recipient is not a member of this group");

        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException(
                    "Unable to validate recipient membership");

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Group Service unavailable");
        }
    }

    public List<UUID> getGroupMemberIds(UUID groupId) {
        try {
            List<UUID> members = restClient.get()
                    .uri("/internal/groups/{groupId}/members", groupId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UUID>>() {
                    });

            return members != null ? members : List.of();

        } catch (HttpClientErrorException.NotFound e) {
            throw new GroupNotFoundException("Group not found: " + groupId);

        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException("Unable to fetch group members for: " + groupId);

        } catch (Exception e) {
            throw new ServiceUnavailableException("Group Service unavailable");
        }
    }
}