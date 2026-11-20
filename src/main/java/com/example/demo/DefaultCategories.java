package com.example.demo;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultCategories implements ApplicationRunner {

	private final ExpenseCategoryRepository categoryRepository;

	public DefaultCategories(ExpenseCategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<String> defaults = List.of(
				"Snacks", "Tea", "Coffee", "Breakfast", "Dinner", "Lunch", "Transport",
				"Mobile Recharge", "Entertainment", "Sports", "Groceries", "Medicine",
				"Fruits", "Clothes", "Others");
		for (String name : defaults) {
			if (!categoryRepository.existsByNameIgnoreCase(name)) {
				categoryRepository.save(new ExpenseCategory(name));
			}
		}
	}
}
