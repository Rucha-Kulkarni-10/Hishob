package com.hishob.group.client;

import com.hishob.group.dto.UserSummaryData;
import com.hishob.group.exception.InvalidTargetUserException;
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
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public AuthServiceClient(
            @Value("${hishob.auth-service.base-url:http://localhost:8081}") String authServiceBaseUrl,
            @Value("${hishob.internal.token}") String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceBaseUrl)
                .build();
        this.internalToken = internalToken;
    }

    public AuthServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
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

}
