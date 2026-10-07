package com.hishob.notification.repository;

import com.hishob.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NotificationRepository — data access for Notification entities.
 *
 * Provides queries for:
 * - Fetching user's notifications (all or unread only)
 * - Counting unread notifications
 * - Finding notification by ID and user (for authorization)
 * - Bulk mark all as read
 *
 * All queries order notifications by createdAt DESC (newest first).
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find all notifications for a user, ordered by createdAt DESC (newest first).
     *
     * @param userId The user ID
     * @return List of notifications
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find unread notifications for a user, ordered by createdAt DESC (newest first).
     *
     * @param userId The user ID
     * @return List of unread notifications
     */
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    /**
     * Count unread notifications for a user.
     *
     * @param userId The user ID
     * @return Count of unread notifications
     */
    Long countByUserIdAndIsReadFalse(UUID userId);

    /**
     * Find a notification by ID and user ID (for ownership verification).
     *
     * @param id     Notification ID
     * @param userId User ID
     * @return Optional notification
     */
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Mark all unread notifications as read for a user.
     * Returns the number of notifications marked as read.
     *
     * @param userId The user ID
     * @return Number of notifications updated
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
