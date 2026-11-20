package com.example.demo;

import java.math.BigDecimal;

public record CategorySpendDto(Long categoryId, String categoryName, BigDecimal amount) {
}
