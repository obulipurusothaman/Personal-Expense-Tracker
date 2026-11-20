package com.example.demo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
	boolean existsByNameIgnoreCase(String name);

	Optional<ExpenseCategory> findByNameIgnoreCase(String name);
}
