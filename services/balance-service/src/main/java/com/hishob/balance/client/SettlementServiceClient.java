package com.hishob.balance.client;

import com.hishob.balance.dto.CompletedSettlementData;
import com.hishob.balance.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * RestClient for communicating with Settlement Service internal API.
 *
 * Calls: GET /internal/groups/{groupId}/settlements/completed
 * Auth: X-Internal-Token header
 *
 * If Settlement Service is unavailable, throws ServiceUnavailableException (→
 * HTTP 503).
 * Never silently returns an empty list on failure — downstream errors must not
 * be hidden.
 */
@Component
public class SettlementServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    // Spring constructor
    @Autowired
    public SettlementServiceClient(
            @Value("${hishob.settlement-service.base-url:http://localhost:8085}") String baseUrl,
            @Value("${hishob.settlement-service.internal-token}") String internalToken) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.internalToken = internalToken;
    }

    // Package-private constructor for unit tests
    SettlementServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    /**
     * Fetches all COMPLETED settlements for the given group from Settlement
     * Service.
     *
     * @throws ServiceUnavailableException if Settlement Service is unreachable or
     *                                     returns
     *                                     an unexpected error. Never returns
     *                                     silently on failure.
     */
    public List<CompletedSettlementData> getCompletedSettlements(UUID groupId) {
        try {
            List<CompletedSettlementData> settlements = restClient.get()
                    .uri("/internal/groups/{groupId}/settlements/completed", groupId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CompletedSettlementData>>() {
                    });

            return settlements != null ? settlements : List.of();

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Settlement Service unavailable: cannot fetch completed settlements for group "
                            + groupId);
        }
    }

    public List<CompletedSettlementData> getCompletedPersonalSettlements(UUID userId) {
        try {
            List<CompletedSettlementData> settlements = restClient.get()
                    .uri("/internal/personal-settlements/users/{userId}/completed", userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CompletedSettlementData>>() {
                    });

            return settlements != null ? settlements : List.of();

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Settlement Service unavailable: cannot fetch completed personal settlements for user "
                            + userId);
        }
    }
}
