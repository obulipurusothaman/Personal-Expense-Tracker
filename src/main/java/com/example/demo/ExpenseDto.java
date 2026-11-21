package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseDto(
		Long id,
		Long accountId,
		String bankName,
		Long categoryId,
		String categoryName,
		String description,
		BigDecimal amount,
		LocalDate expenseDate,
		boolean balanceAdjusted) {

	public static ExpenseDto from(Expense expense) {
		return new ExpenseDto(
				expense.getId(),
				expense.getAccount().getId(),
				expense.getAccount().getBankName(),
				expense.getCategory().getId(),
				expense.getCategory().getName(),
				expense.getDescription(),
				expense.getAmount(),
				expense.getExpenseDate(),
				expense.isBalanceAdjusted());
	}
}
