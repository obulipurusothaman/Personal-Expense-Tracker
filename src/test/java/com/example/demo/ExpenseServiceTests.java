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

	@Autowired
	private AppUserRepository userRepository;

	private AppUser owner;

	@BeforeEach
	void clearData() {
		expenseRepository.deleteAll();
		accountRepository.deleteAll();
		categoryRepository.deleteAll();
		userRepository.deleteAll();
		owner = userRepository.save(new AppUser("expense-tester", "test-password"));
	}

	@Test
	void expenseUpdatesBalanceAndDeletingItRestoresBalance() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Lunch"));

		ExpenseDto expense = expenseService.createExpense(owner, new ExpenseRequest(
				account.id(), category.id(), "Lunch", new BigDecimal("125.50"), LocalDate.now()));

		assertEquals(new BigDecimal("874.50"), expenseService.getAccounts(owner).get(0).balance());

		expenseService.deleteExpense(owner, expense.id());

		assertEquals(new BigDecimal("1000.00"), expenseService.getAccounts(owner).get(0).balance());
	}

	@Test
	void dashboardKeepsEachBankBalanceAndCategorySpendSeparate() {
		AccountDto firstAccount = expenseService.createAccount(owner,
				new AccountRequest("First Bank", new BigDecimal("1000.00")));
		AccountDto secondAccount = expenseService.createAccount(owner,
				new AccountRequest("Second Bank", new BigDecimal("500.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Snacks"));
		LocalDate today = LocalDate.now();

		expenseService.createExpense(owner, new ExpenseRequest(
				firstAccount.id(), category.id(), "Chips", new BigDecimal("50.00"), today));
		expenseService.createExpense(owner, new ExpenseRequest(
				secondAccount.id(), category.id(), "Fruit", new BigDecimal("25.00"), today));

		DashboardDto dashboard = expenseService.getDashboard(owner, "month", today);
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
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Lunch"));
		expenseService.createExpense(owner, new ExpenseRequest(
				account.id(), category.id(), "Lunch", new BigDecimal("125.50"), LocalDate.now()));

		expenseService.deleteAccount(owner, account.id());

		assertEquals(0, expenseService.getAccounts(owner).size());
		assertEquals(0, expenseRepository.count());
	}

	@Test
	void usersCannotReadOrDeleteAnotherUsersAccounts() {
		AppUser otherUser = userRepository.save(new AppUser("other-user", "test-password"));
		expenseService.createAccount(owner, new AccountRequest("Owner Bank", new BigDecimal("100.00")));
		AccountDto othersAccount = expenseService.createAccount(
				otherUser, new AccountRequest("Other Bank", new BigDecimal("200.00")));

		assertEquals(1, expenseService.getAccounts(owner).size());
		assertEquals("Owner Bank", expenseService.getAccounts(owner).get(0).bankName());
		org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> expenseService.deleteAccount(owner, othersAccount.id()));
		assertEquals(1, expenseService.getAccounts(otherUser).size());
	}
}
