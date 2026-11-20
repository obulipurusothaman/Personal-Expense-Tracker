package com.example.demo;

public record CategoryDto(Long id, String name) {
	public static CategoryDto from(ExpenseCategory category) {
		return new CategoryDto(category.getId(), category.getName());
	}
}
