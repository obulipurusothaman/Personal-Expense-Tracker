package com.example.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LegacyImportedExpenseBalanceMigration implements ApplicationRunner {

	private final ExpenseService expenseService;

	public LegacyImportedExpenseBalanceMigration(ExpenseService expenseService) {
		this.expenseService = expenseService;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		expenseService.applyLegacyImportedExpenseBalanceAdjustments();
	}
}
