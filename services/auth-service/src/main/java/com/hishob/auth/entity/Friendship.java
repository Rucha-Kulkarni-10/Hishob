package com.hishob.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "friendships",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_friendship_pair", columnNames = {"user_id_low", "user_id_high"})
        },
        indexes = {
                @Index(name = "idx_friendships_user_low_status", columnList = "user_id_low, status"),
                @Index(name = "idx_friendships_user_high_status", columnList = "user_id_high, status"),
                @Index(name = "idx_friendships_action_user_status", columnList = "action_user_id, status")
        }
)
@Check(constraints = "user_id_low < user_id_high")
public class Friendship {

    private static final java.util.Comparator<UUID> CANONICAL_UUID_ORDER = java.util.Comparator.comparing(UUID::toString);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id_low", nullable = false)
    private UUID userIdLow;

    @Column(name = "user_id_high", nullable = false)
    private UUID userIdHigh;

    @Column(name = "action_user_id", nullable = false)
    private UUID actionUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Friendship() {
    }

    public Friendship(UUID userIdLow, UUID userIdHigh, UUID actionUserId, FriendshipStatus status) {
        if (userIdLow == null || userIdHigh == null) {
            throw new IllegalArgumentException("User IDs cannot be null");
        }
        if (CANONICAL_UUID_ORDER.compare(userIdLow, userIdHigh) >= 0) {
            throw new IllegalArgumentException("user_id_low must be strictly less than user_id_high");
        }
        if (actionUserId == null) {
            throw new IllegalArgumentException("action_user_id cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        this.userIdLow = userIdLow;
        this.userIdHigh = userIdHigh;
        this.actionUserId = actionUserId;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserIdLow() {
        return userIdLow;
    }

    public void setUserIdLow(UUID userIdLow) {
        this.userIdLow = userIdLow;
    }

    public UUID getUserIdHigh() {
        return userIdHigh;
    }

    public void setUserIdHigh(UUID userIdHigh) {
        this.userIdHigh = userIdHigh;
    }

    public UUID getActionUserId() {
        return actionUserId;
    }

    public void setActionUserId(UUID actionUserId) {
        this.actionUserId = actionUserId;
    }

    public FriendshipStatus getStatus() {
        return status;
    }

    public void setStatus(FriendshipStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
