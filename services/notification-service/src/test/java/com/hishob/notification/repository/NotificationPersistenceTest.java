package com.hishob.notification.repository;

import com.hishob.notification.entity.Notification;
import com.hishob.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationPersistenceTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("Persist and retrieve each of the 10 supported NotificationType values")
    void testPersistAndRetrieve_AllTenNotificationTypes() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        for (NotificationType type : NotificationType.values()) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setGroupId(groupId);
            notification.setType(type);
            notification.setMessage("Test message for " + type.name());
            notification.setMetadata(Map.of("testKey", "testVal", "type", type.name()));

            Notification saved = notificationRepository.saveAndFlush(notification);
            assertThat(saved.getId()).isNotNull();

            Optional<Notification> retrieved = notificationRepository.findById(saved.getId());
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getType()).isEqualTo(type);
            assertThat(retrieved.get().getMessage()).isEqualTo("Test message for " + type.name());
            assertThat(retrieved.get().getGroupId()).isEqualTo(groupId);
            assertThat(retrieved.get().getMetadata()).containsEntry("type", type.name());
        }

        List<Notification> userNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(userNotifications).hasSize(NotificationType.values().length);
    }

    @Test
    @DisplayName("Persist personal notification types with null groupId")
    void testPersist_PersonalNotifications_NullGroupId() {
        UUID userId = UUID.randomUUID();

        List<NotificationType> personalTypes = List.of(
                NotificationType.PERSONAL_EXPENSE_ADDED,
                NotificationType.PERSONAL_EXPENSE_UPDATED,
                NotificationType.PERSONAL_EXPENSE_DELETED,
                NotificationType.EXPENSE_COMMENT_ADDED,
                NotificationType.PERSONAL_SETTLEMENT_RECORDED
        );

        for (NotificationType type : personalTypes) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setGroupId(null);
            notification.setType(type);
            notification.setMessage("Personal notification: " + type.name());
            notification.setMetadata(Map.of("personal", "true"));

            Notification saved = notificationRepository.saveAndFlush(notification);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getGroupId()).isNull();

            Optional<Notification> retrieved = notificationRepository.findById(saved.getId());
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getGroupId()).isNull();
            assertThat(retrieved.get().getType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Group notifications remain valid with non-null groupId")
    void testPersist_GroupNotifications_NonNullGroupId() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        List<NotificationType> groupTypes = List.of(
                NotificationType.GROUP_MEMBER_ADDED,
                NotificationType.EXPENSE_ADDED,
                NotificationType.EXPENSE_UPDATED,
                NotificationType.EXPENSE_DELETED,
                NotificationType.SETTLEMENT_RECORDED
        );

        for (NotificationType type : groupTypes) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setGroupId(groupId);
            notification.setType(type);
            notification.setMessage("Group notification: " + type.name());

            Notification saved = notificationRepository.saveAndFlush(notification);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getGroupId()).isEqualTo(groupId);

            Optional<Notification> retrieved = notificationRepository.findById(saved.getId());
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getGroupId()).isEqualTo(groupId);
            assertThat(retrieved.get().getType()).isEqualTo(type);
        }
    }
}
