package com.hishob.expense.client;

import com.hishob.expense.dto.UserSummaryData;
import com.hishob.expense.exception.ExpenseNotFoundException;
import com.hishob.expense.exception.InvalidTargetUserException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.exception.ServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);
    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public AuthServiceClient(
            @Value("${hishob.auth-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${hishob.auth-service.internal-token:${hishob.internal.token:dev-internal-token-hishob-service-2026}}") String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.internalToken = internalToken;
    }

    public AuthServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public void validateAcceptedFriendships(UUID currentUserId, List<UUID> userIds) {
        try {
            ValidateAcceptedFriendshipsResponse response = restClient.post()
                    .uri("/internal/friendships/accepted/validate")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(new ValidateAcceptedFriendshipsRequest(currentUserId, userIds))
                    .retrieve()
                    .body(ValidateAcceptedFriendshipsResponse.class);

            if (response == null) {
                throw new ServiceUnavailableException("Auth Service returned an empty friendship validation response");
            }
            if (response.getMissingUserIds() != null && !response.getMissingUserIds().isEmpty()) {
                throw new ExpenseNotFoundException("One or more personal expense users were not found");
            }
            if (!response.isValid()) {
                throw new PermissionDeniedException("All personal expense users must be accepted friends");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ExpenseNotFoundException("One or more personal expense users were not found");
        } catch (PermissionDeniedException | ExpenseNotFoundException | ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("Auth Service unavailable: cannot validate friendships");
        }
    }

    public void validateUserExists(UUID userId) {
        try {
            restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/auth/users/{userId}/exists")
                            .build(userId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound nf) {
            throw new InvalidTargetUserException("Target user does not exist: " + userId);
        } catch (HttpClientErrorException e) {
            throw new InvalidTargetUserException("Unable to validate user: " + userId + " (status: " + e.getStatusCode() + ")");
        } catch (Exception e) {
            throw new InvalidTargetUserException("Unable to reach Auth Service for user validation: " + userId);
        }
    }

    public List<UserSummaryData> getUsersBatch(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        try {
            String forwardAuthHeader = getIncomingAuthorizationHeader();

            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri("/api/auth/users/batch")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON);

            if (forwardAuthHeader != null && !forwardAuthHeader.isBlank()) {
                requestSpec.header("Authorization", forwardAuthHeader);
            }

            List<UserSummaryData> result = requestSpec
                    .body(new ArrayList<>(userIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserSummaryData>>() {});

            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch user profiles in batch from Auth Service for {} user IDs: {}",
                    userIds.size(), e.getMessage());
            return List.of();
        }
    }

    private String getIncomingAuthorizationHeader() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("Authorization");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class ValidateAcceptedFriendshipsRequest {
        private UUID currentUserId;
        private List<UUID> userIds;

        ValidateAcceptedFriendshipsRequest(UUID currentUserId, List<UUID> userIds) {
            this.currentUserId = currentUserId;
            this.userIds = userIds;
        }

        public UUID getCurrentUserId() {
            return currentUserId;
        }

        public List<UUID> getUserIds() {
            return userIds;
        }
    }

    public static class ValidateAcceptedFriendshipsResponse {
        private boolean valid;
        private List<UUID> missingUserIds;
        private List<UUID> nonAcceptedFriendIds;

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public List<UUID> getMissingUserIds() {
            return missingUserIds;
        }

        public void setMissingUserIds(List<UUID> missingUserIds) {
            this.missingUserIds = missingUserIds;
        }

        public List<UUID> getNonAcceptedFriendIds() {
            return nonAcceptedFriendIds;
        }

        public void setNonAcceptedFriendIds(List<UUID> nonAcceptedFriendIds) {
            this.nonAcceptedFriendIds = nonAcceptedFriendIds;
        }
    }
}
