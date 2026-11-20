package com.example.demo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
	boolean existsByNameIgnoreCase(String name);

	Optional<ExpenseCategory> findByNameIgnoreCase(String name);

	List<ExpenseCategory> findByOwnerIsNull();

	List<ExpenseCategory> findByOwner_Id(Long ownerId);

	boolean existsByOwner_Id(Long ownerId);
}
