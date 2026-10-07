package com.hishob.expense.client;

import com.hishob.expense.exception.InvalidGroupException;
import com.hishob.expense.exception.InvalidSplitException;
import com.hishob.expense.exception.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class GroupServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    // Spring constructor
    @Autowired
    public GroupServiceClient(
            @Value("${hishob.group-service.base-url:http://localhost:8082}") String groupServiceBaseUrl,
            @Value("${hishob.group-service.internal-token}") String internalToken) {

        this.restClient = RestClient.builder()
                .baseUrl(groupServiceBaseUrl)
                .build();

        this.internalToken = internalToken;
    }

    // Test constructor
    GroupServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public void validateGroupExists(UUID groupId) {
        try {
            restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/groups/{groupId}/exists")
                            .build(groupId))
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound nf) {
            throw new InvalidGroupException(
                    "Group not found: " + groupId
            );

        } catch (HttpClientErrorException e) {
            throw new InvalidGroupException(
                    "Unable to validate group: "
                            + groupId
                            + " (status: "
                            + e.getStatusCode()
                            + ")"
            );

        } catch (Exception e) {
            throw new InvalidGroupException(
                    "Unable to reach Group Service for group validation: "
                            + groupId
            );
        }
    }

    public void validateMember(
            UUID groupId,
            UUID userId,
            String roleLabel) {

        try {
            restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/groups/{groupId}/members/{userId}/exists")
                            .build(groupId, userId))
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound nf) {
            throw new PermissionDeniedException(
                    roleLabel + " is not a member of this group"
            );

        } catch (HttpClientErrorException e) {
            throw new InvalidSplitException(
                    "Unable to validate membership for "
                            + roleLabel
                            + ": "
                            + userId
                            + " (status: "
                            + e.getStatusCode()
                            + ")"
            );

        } catch (Exception e) {
            throw new InvalidSplitException(
                    "Unable to reach Group Service for membership validation: "
                            + userId
            );
        }
    }

    public List<UUID> getGroupMemberIds(UUID groupId) {
        try {
            List<UUID> members = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/groups/{groupId}/members")
                            .build(groupId))
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UUID>>() {});

            return members != null ? members : List.of();

        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidGroupException("Group not found: " + groupId);

        } catch (Exception e) {
            throw new InvalidGroupException("Unable to reach Group Service to fetch members: " + groupId);
        }
    }
}