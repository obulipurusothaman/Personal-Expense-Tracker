package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
	void deleteByAccount_Id(Long accountId);

	Optional<Expense> findByIdAndAccount_Owner_Id(Long id, Long ownerId);

	List<Expense> findByAccount_Owner_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long ownerId, LocalDate startDate, LocalDate endDate);

	List<Expense> findByAccount_Owner_IdAndAccount_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long ownerId, Long accountId, LocalDate startDate, LocalDate endDate);

	List<Expense> findByAccount_Owner_IdAndAccount_IdAndCategory_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long ownerId, Long accountId, Long categoryId, LocalDate startDate, LocalDate endDate);

	List<Expense> findByAccount_Owner_IdAndCategory_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
			Long ownerId, Long categoryId, LocalDate startDate, LocalDate endDate);
}
