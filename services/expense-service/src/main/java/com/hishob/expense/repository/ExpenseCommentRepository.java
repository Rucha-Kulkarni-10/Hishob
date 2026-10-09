package com.hishob.expense.repository;

import com.hishob.expense.entity.ExpenseComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseCommentRepository extends JpaRepository<ExpenseComment, UUID> {

    List<ExpenseComment> findByExpenseIdOrderByCreatedAtAsc(UUID expenseId);

    Optional<ExpenseComment> findByIdAndExpenseId(UUID id, UUID expenseId);
}
