package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StatementImportPreviewDto(
		String detectedBankName,
		Long suggestedAccountId,
		String accountMatchMessage,
		int skippedCredits,
		int skippedRows,
		List<Row> rows) {

	public record Row(
			int rowNumber,
			LocalDate expenseDate,
			String description,
			BigDecimal amount,
			Long suggestedCategoryId,
			String suggestedCategoryName,
			boolean needsReview,
			boolean assumedDebit,
			boolean duplicate,
			boolean include) {
	}
}
