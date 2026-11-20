package com.example.demo;

import java.math.BigDecimal;
import java.util.List;

public record AccountSummaryDto(
		Long id,
		String bankName,
		BigDecimal balance,
		BigDecimal spent,
		List<CategorySpendDto> categoryBreakdown,
		List<ExpenseDto> recentExpenses) {
}
