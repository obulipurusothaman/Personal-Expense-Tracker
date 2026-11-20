package com.example.demo;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultCategories implements ApplicationRunner {

	public static final List<String> NAMES = List.of(
			"Snacks", "Tea", "Coffee", "Breakfast", "Dinner", "Lunch", "Transport",
			"Mobile Recharge", "Entertainment", "Sports", "Groceries", "Medicine",
			"Fruits", "Clothes", "Others");

	private final ExpenseCategoryRepository categoryRepository;

	public DefaultCategories(ExpenseCategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (String name : NAMES) {
			ExpenseCategory category = categoryRepository.findByNameIgnoreCase(name)
					.orElseGet(() -> new ExpenseCategory(name, null));
			categoryRepository.save(category);
		}
	}

	public static boolean isBuiltIn(String categoryName) {
		return NAMES.stream().anyMatch(name -> name.equalsIgnoreCase(categoryName));
	}
}
