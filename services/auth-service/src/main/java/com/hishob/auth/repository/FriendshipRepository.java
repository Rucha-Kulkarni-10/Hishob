package com.hishob.auth.repository;

import com.hishob.auth.entity.Friendship;
import com.hishob.auth.entity.FriendshipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    Optional<Friendship> findByUserIdLowAndUserIdHigh(UUID userIdLow, UUID userIdHigh);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Friendship f WHERE f.id = :id")
    Optional<Friendship> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Friendship f WHERE f.userIdLow = :userIdLow AND f.userIdHigh = :userIdHigh")
    Optional<Friendship> findByPairForUpdate(
            @Param("userIdLow") UUID userIdLow,
            @Param("userIdHigh") UUID userIdHigh);

    @Query("SELECT f FROM Friendship f WHERE (f.userIdLow = :userId OR f.userIdHigh = :userId) AND f.actionUserId != :userId AND f.status = :status ORDER BY f.createdAt DESC")
    List<Friendship> findIncomingRequests(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f WHERE f.actionUserId = :userId AND f.status = :status ORDER BY f.createdAt DESC")
    List<Friendship> findOutgoingRequests(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f WHERE (f.userIdLow = :userId OR f.userIdHigh = :userId) AND f.status = :status ORDER BY f.createdAt DESC")
    List<Friendship> findAllByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

}
