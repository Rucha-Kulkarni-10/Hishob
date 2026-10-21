package com.hishob.settlement.client;

import com.hishob.settlement.dto.InternalExpenseData;
import com.hishob.settlement.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class ExpenseServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    // Spring constructor
    @Autowired
    public ExpenseServiceClient(
            @Value("${hishob.expense-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${hishob.expense-service.internal-token:${hishob.internal.token:dev-internal-token-hishob-service-2026}}") String internalToken) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.internalToken = internalToken;
    }

    // Test constructor
    public ExpenseServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public List<InternalExpenseData> getGroupExpenses(UUID groupId) {
        try {
            List<InternalExpenseData> expenses = restClient.get()
                    .uri("/internal/groups/{groupId}/expenses", groupId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalExpenseData>>() {
                    });

            return expenses != null ? expenses : List.of();

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Expense Service unavailable: cannot fetch expenses");
        }
    }

    public List<InternalExpenseData> getPersonalExpensesForUser(UUID userId) {
        try {
            List<InternalExpenseData> expenses = restClient.get()
                    .uri("/internal/personal-expenses/users/{userId}", userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalExpenseData>>() {
                    });

            return expenses != null ? expenses : List.of();

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Expense Service unavailable: cannot fetch personal expenses");
        }
    }
}
