package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
	void deleteByAccountId(Long accountId);

	List<Expense> findByAccountIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long accountId, LocalDate startDate, LocalDate endDate);

	List<Expense> findByAccountIdAndCategoryIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long accountId, Long categoryId, LocalDate startDate, LocalDate endDate);

	List<Expense> findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(LocalDate startDate, LocalDate endDate);

	List<Expense> findByCategoryIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long categoryId, LocalDate startDate, LocalDate endDate);
}
