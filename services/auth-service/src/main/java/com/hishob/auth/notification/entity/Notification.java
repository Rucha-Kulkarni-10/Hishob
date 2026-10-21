package com.hishob.auth.notification.entity;

import com.hishob.auth.notification.enums.NotificationType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notification entity — represents an in-app notification for a user.
 *
 * Stores notification events triggered by actions across the system:
 * - Group membership changes
 * - Expense creation/update/deletion
 * - Settlement recording
 *
 * Fields:
 * - id: Primary key (UUID)
 * - userId: Recipient user ID (UUID, indexed)
 * - groupId: Related group ID (UUID, nullable)
 * - type: Notification category (enum)
 * - message: Human-readable notification text
 * - isRead: Read/unread status (boolean, indexed)
 * - createdAt: Notification timestamp
 * - readAt: When notification was marked as read (nullable)
 * - metadata: Additional context as JSON (nullable)
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_user_id_is_read", columnList = "user_id, is_read"),
        @Index(name = "idx_notifications_created_at", columnList = "created_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Constructors

    public Notification() {
    }

    public Notification(UUID userId, UUID groupId, NotificationType type, String message, Map<String, Object> metadata) {
        this.userId = userId;
        this.groupId = groupId;
        this.type = type;
        this.message = message;
        this.metadata = metadata;
        this.isRead = false;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Convenience method
    public boolean isRead() {
        return Boolean.TRUE.equals(isRead);
    }
}
