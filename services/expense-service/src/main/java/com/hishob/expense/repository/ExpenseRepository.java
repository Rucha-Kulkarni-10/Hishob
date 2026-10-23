package com.hishob.expense.repository;

import com.hishob.expense.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @EntityGraph(attributePaths = {"splits"})
    List<Expense> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

    @EntityGraph(attributePaths = {"splits"})
    List<Expense> findByGroupIdIn(Collection<UUID> groupIds);

    @EntityGraph(attributePaths = {"splits"})
    List<Expense> findByGroupIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            UUID groupId, java.time.Instant startDate);

    @EntityGraph(attributePaths = {"splits"})
    List<Expense> findByGroupIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            UUID groupId, java.time.Instant endDate);

    @EntityGraph(attributePaths = {"splits"})
    List<Expense> findByGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID groupId, java.time.Instant startDate, java.time.Instant endDate);

    @EntityGraph(attributePaths = {"splits"})
    @Query("""
            SELECT DISTINCT e
            FROM Expense e
            LEFT JOIN e.splits s
            WHERE e.groupId IS NULL
              AND (e.createdBy = :userId OR e.paidBy = :userId OR s.userId = :userId)
            ORDER BY e.createdAt DESC
            """)
    List<Expense> findPersonalExpensesForUser(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"splits"})
    @Query("""
            SELECT DISTINCT e
            FROM Expense e
            LEFT JOIN e.splits s
            WHERE e.id = :expenseId
              AND e.groupId IS NULL
              AND (e.createdBy = :userId OR e.paidBy = :userId OR s.userId = :userId)
            """)
    Optional<Expense> findPersonalExpenseByIdForUser(
            @Param("expenseId") UUID expenseId,
            @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"splits"})
    @Query("""
            SELECT DISTINCT e
            FROM Expense e
            LEFT JOIN e.splits s
            WHERE e.groupId IS NULL
              AND (e.createdBy = :userA OR e.paidBy = :userA OR s.userId = :userA)
              AND (
                    e.createdBy = :userB
                 OR e.paidBy = :userB
                 OR EXISTS (
                        SELECT 1
                        FROM ExpenseSplit s2
                        WHERE s2.expense = e AND s2.userId = :userB
                    )
              )
            ORDER BY e.createdAt DESC
            """)
    List<Expense> findPersonalExpensesBetweenUsers(
            @Param("userA") UUID userA,
            @Param("userB") UUID userB);
}
