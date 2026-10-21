package com.hishob.settlement.client;

import com.hishob.settlement.dto.CreateNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public NotificationServiceClient(
            @Value("${hishob.auth-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${hishob.auth-service.internal-token}") String internalToken) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .build(), internalToken);
    }

    NotificationServiceClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
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
        } catch (HttpStatusCodeException e) {
            // Log HTTP status and status text without sensitive tokens or request bodies
            log.warn("Failed to create notification of type {} for user {}: HTTP {} {}", 
                    request.getType(), request.getUserId(), e.getStatusCode().value(), e.getStatusText());
        } catch (ResourceAccessException e) {
            // Service connectivity or timeout issues
            log.warn("Notification Service is unreachable when sending {} to user {}: {}", 
                    request.getType(), request.getUserId(), e.getMessage());
        } catch (Exception e) {
            // General fallback: log message without failing main operation
            log.warn("Failed to create notification of type {} for user {}: {}", 
                    request.getType(), request.getUserId(), e.getMessage());
        }
    }

}