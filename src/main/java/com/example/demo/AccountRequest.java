package com.example.demo;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountRequest(
		@NotBlank @Size(max = 80) String bankName,
		@NotNull @DecimalMin("0.00") BigDecimal balance) {
}
