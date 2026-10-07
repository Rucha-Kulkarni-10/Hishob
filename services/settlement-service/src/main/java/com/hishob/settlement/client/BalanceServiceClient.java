package com.hishob.settlement.client;

import com.hishob.settlement.dto.InternalBalanceData;
import com.hishob.settlement.dto.PersonalDebtSimplificationResponse;
import com.hishob.settlement.exception.GroupNotFoundException;
import com.hishob.settlement.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class BalanceServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public BalanceServiceClient(
            @Value("${hishob.balance-service.base-url:http://localhost:8084}") String baseUrl,
            @Value("${hishob.balance-service.internal-token}") String internalToken) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.internalToken = internalToken;
    }

    BalanceServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public List<InternalBalanceData> getGroupBalances(UUID groupId) {
        try {
            GroupBalanceWrapper wrapper = restClient.get()
                    .uri("/internal/groups/{groupId}/balances", groupId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(GroupBalanceWrapper.class);

            if (wrapper == null || wrapper.getBalances() == null) {
                return List.of();
            }

            return wrapper.getBalances().stream()
                    .map(b -> {
                        InternalBalanceData d = new InternalBalanceData();
                        d.setUserId(b.getUserId());
                        d.setNetBalance(b.getNetBalance());
                        return d;
                    })
                    .toList();

        } catch (HttpClientErrorException.NotFound e) {
            throw new GroupNotFoundException(
                    "Group not found: " + groupId);

        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException(
                    "Balance Service error: " + e.getMessage());

        } catch (GroupNotFoundException e) {
            throw e;

        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Balance Service unavailable");
        }
    }

    public PersonalDebtSimplificationResponse getPersonalSimplification(UUID userId) {
        try {
            PersonalDebtSimplificationResponse response = restClient.get()
                    .uri("/internal/personal-balances/users/{userId}/simplify", userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(PersonalDebtSimplificationResponse.class);

            if (response == null) {
                throw new ServiceUnavailableException("Balance Service returned an empty personal simplification response");
            }
            return response;
        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException(
                    "Balance Service error: " + e.getMessage());
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Balance Service unavailable");
        }
    }

    static class GroupBalanceWrapper {

        private UUID groupId;
        private List<BalanceEntry> balances;

        public UUID getGroupId() {
            return groupId;
        }

        public void setGroupId(UUID groupId) {
            this.groupId = groupId;
        }

        public List<BalanceEntry> getBalances() {
            return balances;
        }

        public void setBalances(List<BalanceEntry> balances) {
            this.balances = balances;
        }
    }

    static class BalanceEntry {

        private UUID userId;
        private java.math.BigDecimal netBalance;

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public java.math.BigDecimal getNetBalance() {
            return netBalance;
        }

        public void setNetBalance(java.math.BigDecimal netBalance) {
            this.netBalance = netBalance;
        }
    }
}
