package com.example.demo;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(
		BigDecimal totalSpent,
		List<AccountSummaryDto> accounts,
		List<CategoryDto> categories) {
}
