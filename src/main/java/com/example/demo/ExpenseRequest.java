package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExpenseRequest(
		@NotNull Long accountId,
		@NotNull Long categoryId,
		@Size(max = 120) String description,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotNull LocalDate expenseDate,
		@Pattern(regexp = "[0-9a-fA-F-]{36}") String clientRequestId) {

	public ExpenseRequest(
			Long accountId, Long categoryId, String description, BigDecimal amount, LocalDate expenseDate) {
		this(accountId, categoryId, description, amount, expenseDate, null);
	}
}
