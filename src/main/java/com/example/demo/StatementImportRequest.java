package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StatementImportRequest(
		@NotNull Long accountId,
		@NotEmpty @Size(max = 2000) List<@Valid Row> rows) {

	public record Row(
			@NotNull Long categoryId,
			@NotBlank @Size(max = 120) String description,
			@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@NotNull LocalDate expenseDate,
			@NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String clientRequestId) {
	}
}
