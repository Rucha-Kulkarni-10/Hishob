package com.hishob.expense.repository;

import com.hishob.expense.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {

    List<ExpenseSplit> findByExpenseId(UUID expenseId);

    @Modifying
    @Transactional
    void deleteByExpenseId(UUID expenseId);

}
