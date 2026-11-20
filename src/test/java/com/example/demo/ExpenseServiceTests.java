package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:expense-service-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExpenseServiceTests {

	@Autowired
	private ExpenseService expenseService;

	@Autowired
	private ExpenseRepository expenseRepository;

	@Autowired
	private ExpenseCategoryRepository categoryRepository;

	@Autowired
	private BankAccountRepository accountRepository;

	@BeforeEach
	void clearData() {
		expenseRepository.deleteAll();
		categoryRepository.deleteAll();
		accountRepository.deleteAll();
	}

	@Test
	void expenseUpdatesBalanceAndDeletingItRestoresBalance() {
		AccountDto account = expenseService.createAccount(
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(new CategoryRequest("Lunch"));

		ExpenseDto expense = expenseService.createExpense(new ExpenseRequest(
				account.id(), category.id(), "Lunch", new BigDecimal("125.50"), LocalDate.now()));

		assertEquals(new BigDecimal("874.50"), expenseService.getAccounts().get(0).balance());

		expenseService.deleteExpense(expense.id());

		assertEquals(new BigDecimal("1000.00"), expenseService.getAccounts().get(0).balance());
	}

	@Test
	void dashboardKeepsEachBankBalanceAndCategorySpendSeparate() {
		AccountDto firstAccount = expenseService.createAccount(
				new AccountRequest("First Bank", new BigDecimal("1000.00")));
		AccountDto secondAccount = expenseService.createAccount(
				new AccountRequest("Second Bank", new BigDecimal("500.00")));
		CategoryDto category = expenseService.createCategory(new CategoryRequest("Snacks"));
		LocalDate today = LocalDate.now();

		expenseService.createExpense(new ExpenseRequest(
				firstAccount.id(), category.id(), "Chips", new BigDecimal("50.00"), today));
		expenseService.createExpense(new ExpenseRequest(
				secondAccount.id(), category.id(), "Fruit", new BigDecimal("25.00"), today));

		DashboardDto dashboard = expenseService.getDashboard("month", today);
		AccountSummaryDto firstSummary = dashboard.accounts().stream()
				.filter(account -> account.id().equals(firstAccount.id())).findFirst().orElseThrow();
		AccountSummaryDto secondSummary = dashboard.accounts().stream()
				.filter(account -> account.id().equals(secondAccount.id())).findFirst().orElseThrow();

		assertEquals(new BigDecimal("950.00"), firstSummary.balance());
		assertEquals(new BigDecimal("50.00"), firstSummary.spent());
		assertEquals("Snacks", firstSummary.categoryBreakdown().get(0).categoryName());
		assertEquals(new BigDecimal("475.00"), secondSummary.balance());
		assertEquals(new BigDecimal("25.00"), secondSummary.spent());
		assertEquals("Snacks", secondSummary.categoryBreakdown().get(0).categoryName());
	}

	@Test
	void deletingAccountAlsoDeletesItsExpenses() {
		AccountDto account = expenseService.createAccount(
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(new CategoryRequest("Lunch"));
		expenseService.createExpense(new ExpenseRequest(
				account.id(), category.id(), "Lunch", new BigDecimal("125.50"), LocalDate.now()));

		expenseService.deleteAccount(account.id());

		assertEquals(0, expenseService.getAccounts().size());
		assertEquals(0, expenseRepository.count());
	}
}
