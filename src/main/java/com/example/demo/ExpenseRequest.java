package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExpenseRequest(
		@NotNull Long accountId,
		@NotNull Long categoryId,
		@Size(max = 120) String description,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotNull LocalDate expenseDate) {
}
