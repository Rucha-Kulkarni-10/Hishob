package com.hishob.expense.client;

import com.hishob.expense.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RestClient for communicating with Settlement Service internal API.
 * Calls: GET /internal/groups/{groupId}/settlements/completed
 * Auth: X-Internal-Token header
 */
@Component
public class SettlementServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    public static class CompletedSettlementData {
        private UUID fromUserId;
        private UUID toUserId;
        private BigDecimal amount;

        public CompletedSettlementData() {
        }

        public CompletedSettlementData(UUID fromUserId, UUID toUserId, BigDecimal amount) {
            this.fromUserId = fromUserId;
            this.toUserId = toUserId;
            this.amount = amount;
        }

        public UUID getFromUserId() {
            return fromUserId;
        }

        public void setFromUserId(UUID fromUserId) {
            this.fromUserId = fromUserId;
        }

        public UUID getToUserId() {
            return toUserId;
        }

        public void setToUserId(UUID toUserId) {
            this.toUserId = toUserId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    @Autowired
    public SettlementServiceClient(
            @Value("${hishob.settlement-service.base-url:http://localhost:8085}") String baseUrl,
            @Value("${hishob.settlement-service.internal-token:${hishob.internal.token}}") String internalToken) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.internalToken = internalToken;
    }

    SettlementServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public List<CompletedSettlementData> getCompletedSettlements(UUID groupId) {
        try {
            List<CompletedSettlementData> settlements = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/groups/{groupId}/settlements/completed")
                            .build(groupId))
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CompletedSettlementData>>() {});

            return settlements != null ? settlements : List.of();

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Settlement Service unavailable: cannot fetch completed settlements for group " + groupId);
        }
    }
}
