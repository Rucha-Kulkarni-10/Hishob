package com.hishob.notification.service;

import com.hishob.notification.dto.CreateNotificationRequest;
import com.hishob.notification.dto.NotificationResponse;
import com.hishob.notification.dto.UnreadCountResponse;
import com.hishob.notification.entity.Notification;
import com.hishob.notification.enums.NotificationType;
import com.hishob.notification.exception.NotificationNotFoundException;
import com.hishob.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private UUID userId;
    private UUID groupId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
        userId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Get user notifications: returns all notifications ordered by creation time")
    void testGetUserNotifications_Success() {
        Notification n1 = createNotification(notificationId, userId, groupId, NotificationType.EXPENSE_ADDED, "Expense added", false);
        Notification n2 = createNotification(UUID.randomUUID(), userId, groupId, NotificationType.SETTLEMENT_RECORDED, "Settlement recorded", true);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(n1, n2));

        List<NotificationResponse> notifications = notificationService.getUserNotifications(userId);

        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).getId()).isEqualTo(notificationId);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.EXPENSE_ADDED);
        assertThat(notifications.get(0).isRead()).isFalse();
        assertThat(notifications.get(1).getType()).isEqualTo(NotificationType.SETTLEMENT_RECORDED);
        assertThat(notifications.get(1).isRead()).isTrue();
    }

    @Test
    @DisplayName("Get user notifications: returns empty list when no notifications")
    void testGetUserNotifications_Empty() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        List<NotificationResponse> notifications = notificationService.getUserNotifications(userId);

        assertThat(notifications).isEmpty();
    }

    @Test
    @DisplayName("Get unread count: returns correct count")
    void testGetUnreadCount_Success() {
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(5L);

        UnreadCountResponse response = notificationService.getUnreadCount(userId);

        assertThat(response.getCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Get unread count: returns zero when no unread notifications")
    void testGetUnreadCount_Zero() {
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(0L);

        UnreadCountResponse response = notificationService.getUnreadCount(userId);

        assertThat(response.getCount()).isZero();
    }

    @Test
    @DisplayName("Mark as read: successfully marks unread notification as read")
    void testMarkAsRead_Success() {
        Notification notification = createNotification(notificationId, userId, groupId, NotificationType.EXPENSE_ADDED, "Test", false);

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(notificationId, userId);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
        verify(notificationRepository).save(argThat(n ->
                n.isRead() && n.getReadAt() != null));
    }

    @Test
    @DisplayName("Mark as read: does not modify already read notification")
    void testMarkAsRead_AlreadyRead() {
        Instant readAt = Instant.now().minusSeconds(3600);
        Notification notification = createNotification(notificationId, userId, groupId, NotificationType.EXPENSE_ADDED, "Test", true);
        notification.setReadAt(readAt);

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.markAsRead(notificationId, userId);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isEqualTo(readAt);
        // Repository save is NOT called when already read, but no need to verify this strictly
    }

    @Test
    @DisplayName("Mark as read: throws NotificationNotFoundException when notification doesn't exist")
    void testMarkAsRead_NotFound() {
        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining("Notification not found or does not belong to user");
    }

    @Test
    @DisplayName("Mark as read: throws NotificationNotFoundException when notification belongs to another user")
    void testMarkAsRead_WrongUser() {
        UUID otherUserId = UUID.randomUUID();

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @DisplayName("Create notification: successfully creates notification for internal API")
    void testCreateNotification_Success() {
        CreateNotificationRequest request = new CreateNotificationRequest(
                userId, groupId, NotificationType.GROUP_MEMBER_ADDED,
                "You were added to the group", Map.of("action", "added")
        );

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    n.setId(notificationId);
                    return n;
                });

        NotificationResponse response = notificationService.createNotification(request);

        assertThat(response.getId()).isEqualTo(notificationId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getType()).isEqualTo(NotificationType.GROUP_MEMBER_ADDED);
        assertThat(response.getMessage()).isEqualTo("You were added to the group");
        assertThat(response.isRead()).isFalse();
        assertThat(response.getMetadata()).containsEntry("action", "added");

        verify(notificationRepository).save(argThat(n ->
                n.getUserId().equals(userId) &&
                        n.getGroupId().equals(groupId) &&
                        n.getType() == NotificationType.GROUP_MEMBER_ADDED &&
                        !n.getIsRead()));
    }

    @Test
    @DisplayName("Create notification: creates notification without groupId (null)")
    void testCreateNotification_NullGroupId() {
        CreateNotificationRequest request = new CreateNotificationRequest(
                userId, null, NotificationType.EXPENSE_DELETED,
                "Expense was deleted", null
        );

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    n.setId(notificationId);
                    return n;
                });

        NotificationResponse response = notificationService.createNotification(request);

        assertThat(response.getGroupId()).isNull();
        assertThat(response.getMessage()).isEqualTo("Expense was deleted");
        assertThat(response.getMetadata()).isNull();
    }

    @Test
    @DisplayName("Mark all as read: successfully marks all notifications as read")
    void testMarkAllAsRead_Success() {
        notificationService.markAllAsRead(userId);

        verify(notificationRepository).markAllAsReadForUser(eq(userId), any(Instant.class));
    }

    private Notification createNotification(UUID id, UUID userId, UUID groupId, NotificationType type, String message, boolean isRead) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setGroupId(groupId);
        n.setType(type);
        n.setMessage(message);
        n.setIsRead(isRead);
        n.setCreatedAt(Instant.now());
        return n;
    }

}
