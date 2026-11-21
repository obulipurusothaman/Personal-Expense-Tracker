package com.example.demo;

import java.util.List;

public record OfflineSnapshotDto(
		List<AccountDto> accounts,
		List<CategoryDto> categories,
		List<ExpenseDto> expenses) {
}
