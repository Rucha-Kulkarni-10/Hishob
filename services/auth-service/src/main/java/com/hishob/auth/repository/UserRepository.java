package com.hishob.auth.repository;

import com.hishob.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    java.util.List<User> findAllByIdIn(java.util.Collection<UUID> ids);

    @Query("SELECT u FROM User u WHERE (LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) AND u.id != :excludeUserId ORDER BY u.name ASC")
    java.util.List<User> searchUsers(
            @Param("query") String query,
            @Param("excludeUserId") UUID excludeUserId,
            org.springframework.data.domain.Pageable pageable);

}
