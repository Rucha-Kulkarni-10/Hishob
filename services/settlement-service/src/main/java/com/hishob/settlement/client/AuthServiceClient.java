package com.hishob.settlement.client;

import com.hishob.settlement.exception.GroupNotFoundException;
import com.hishob.settlement.exception.PermissionDeniedException;
import com.hishob.settlement.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class AuthServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public AuthServiceClient(
            @Value("${hishob.auth-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${hishob.auth-service.internal-token}") String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.internalToken = internalToken;
    }

    AuthServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public void validateAcceptedFriendship(UUID currentUserId, UUID friendUserId) {
        try {
            ValidateAcceptedFriendshipsResponse response = restClient.post()
                    .uri("/internal/friendships/accepted/validate")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(new ValidateAcceptedFriendshipsRequest(currentUserId, List.of(friendUserId)))
                    .retrieve()
                    .body(ValidateAcceptedFriendshipsResponse.class);

            if (response == null) {
                throw new ServiceUnavailableException("Auth Service returned an empty friendship validation response");
            }
            if (response.getMissingUserIds() != null && !response.getMissingUserIds().isEmpty()) {
                throw new GroupNotFoundException("One or more users were not found");
            }
            if (!response.isValid()) {
                throw new PermissionDeniedException("Accepted friendship is required");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new GroupNotFoundException("One or more users were not found");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new PermissionDeniedException("Accepted friendship is required");
        } catch (PermissionDeniedException | GroupNotFoundException | ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("Auth Service unavailable: cannot validate friendship");
        }
    }

    static class ValidateAcceptedFriendshipsRequest {
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

    static class ValidateAcceptedFriendshipsResponse {
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
