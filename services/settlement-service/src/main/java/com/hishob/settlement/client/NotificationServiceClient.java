package com.hishob.settlement.client;

import com.hishob.settlement.dto.CreateNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    public NotificationServiceClient(
            @Value("${hishob.notification-service.base-url:http://localhost:8086}") String baseUrl,
            @Value("${hishob.notification-service.internal-token:test-token}") String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.internalToken = internalToken;
    }

    public void createNotification(CreateNotificationRequest request) {
        try {
            restClient.post()
                    .uri("/internal/notifications")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Log but don't fail the main operation
            log.warn("Failed to create notification for user {}: {}", 
                    request.getUserId(), e.getMessage());
        }
    }

}