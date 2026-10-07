package com.hishob.notification.dto;

/**
 * UnreadCountResponse — simple DTO for returning unread notification count.
 *
 * Used by GET /api/notifications/unread-count endpoint.
 */
public class UnreadCountResponse {

    private Long count;

    public UnreadCountResponse() {
    }

    public UnreadCountResponse(Long count) {
        this.count = count;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
