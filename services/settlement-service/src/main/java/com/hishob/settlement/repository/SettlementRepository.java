package com.hishob.settlement.repository;

import com.hishob.settlement.entity.Settlement;
import com.hishob.settlement.entity.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

    List<Settlement> findByGroupIdAndStatus(UUID groupId, SettlementStatus status);

    Optional<Settlement> findTopByGroupIdAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID groupId, UUID fromUserId, UUID toUserId, BigDecimal amount, Instant after);

    @Query("""
            SELECT s FROM Settlement s
            WHERE s.groupId IS NULL
              AND s.status = :status
              AND (s.fromUserId = :userId OR s.toUserId = :userId)
            ORDER BY s.createdAt DESC
            """)
    List<Settlement> findPersonalByUserIdAndStatus(UUID userId, SettlementStatus status);

    @Query("""
            SELECT s FROM Settlement s
            WHERE s.groupId IS NULL
              AND (s.fromUserId = :userId OR s.toUserId = :userId)
            ORDER BY s.createdAt DESC
            """)
    List<Settlement> findPersonalHistoryForUser(UUID userId);

    @Query("""
            SELECT s FROM Settlement s
            WHERE s.groupId IS NULL
              AND ((s.fromUserId = :userA AND s.toUserId = :userB)
                   OR (s.fromUserId = :userB AND s.toUserId = :userA))
            ORDER BY s.createdAt DESC
            """)
    List<Settlement> findPersonalHistoryBetweenUsers(UUID userA, UUID userB);

    Optional<Settlement> findTopByGroupIdIsNullAndFromUserIdAndToUserIdAndAmountAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID fromUserId, UUID toUserId, BigDecimal amount, Instant after);

}
