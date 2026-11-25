package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:expense-service-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExpenseServiceTests {

	@Autowired
	private ExpenseService expenseService;

	@Autowired
	private StatementImportService statementImportService;

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
	void retryingAnOfflineAccountRequestDoesNotCreateDuplicateAccounts() {
		String requestId = UUID.randomUUID().toString();
		AccountRequest request = new AccountRequest("Example Bank", new BigDecimal("1000.00"), requestId);

		AccountDto first = expenseService.createAccount(owner, request);
		AccountDto retry = expenseService.createAccount(owner, request);

		assertEquals(first.id(), retry.id());
		assertEquals(1, accountRepository.findByOwner_Id(owner.getId()).size());
	}

	@Test
	void retryingAnOfflineExpenseRequestDoesNotDeductTheBalanceTwice() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Lunch"));
		String requestId = UUID.randomUUID().toString();
		ExpenseRequest request = new ExpenseRequest(
				account.id(), category.id(), "Lunch", new BigDecimal("125.50"), LocalDate.now(), requestId);

		ExpenseDto first = expenseService.createExpense(owner, request);
		ExpenseDto retry = expenseService.createExpense(owner, request);

		assertEquals(first.id(), retry.id());
		assertEquals(new BigDecimal("874.50"), expenseService.getAccounts(owner).get(0).balance());
		assertEquals(1, expenseRepository.count());
	}

	@Test
	void manualAndImportedExpensesReduceBalanceAndDeletionRestoresIt() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Coffee"));
		ExpenseDto manualExpense = expenseService.createExpense(owner, new ExpenseRequest(
				account.id(), category.id(), "Coffee", new BigDecimal("100.00"), LocalDate.now()));
		String requestId = UUID.randomUUID().toString();
		StatementImportRequest request = new StatementImportRequest(account.id(), List.of(
				new StatementImportRequest.Row(
						category.id(), "Coffee shop", new BigDecimal("25.00"), LocalDate.now(), requestId)));

		StatementImportResult imported = expenseService.importExpenses(owner, request);
		StatementImportResult retried = expenseService.importExpenses(owner, request);

		assertEquals(1, imported.imported());
		assertEquals(1, retried.alreadyImported());
		assertEquals(new BigDecimal("875.00"), expenseService.getAccounts(owner).get(0).balance());
		Expense expense = expenseRepository.findByAccount_Owner_IdOrderByExpenseDateDescIdDesc(owner.getId())
				.get(0);
		assertEquals(true, expense.isBalanceAdjusted());

		expenseService.deleteExpense(owner, manualExpense.id());
		assertEquals(new BigDecimal("975.00"), expenseService.getAccounts(owner).get(0).balance());
		expenseService.deleteExpense(owner, expense.getId());
		assertEquals(new BigDecimal("1000.00"), expenseService.getAccounts(owner).get(0).balance());
	}

	@Test
	void importingExpensesMayShowANegativeRemainingBalance() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("50.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Coffee"));
		StatementImportRequest request = new StatementImportRequest(account.id(), List.of(
				new StatementImportRequest.Row(
						category.id(), "Coffee shop", new BigDecimal("75.00"), LocalDate.now(),
						UUID.randomUUID().toString())));

		expenseService.importExpenses(owner, request);

		assertEquals(new BigDecimal("-25.00"), expenseService.getAccounts(owner).get(0).balance());
	}

	@Test
	void legacyImportedExpensesAreDeductedExactlyOnce() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Coffee"));
		Expense legacyExpense = expenseRepository.save(new Expense(
				accountRepository.findById(account.id()).orElseThrow(),
				categoryRepository.findById(category.id()).orElseThrow(),
				"Older import", new BigDecimal("100.00"), LocalDate.now(), UUID.randomUUID().toString(), false));

		expenseService.applyLegacyImportedExpenseBalanceAdjustments();
		expenseService.applyLegacyImportedExpenseBalanceAdjustments();

		assertEquals(new BigDecimal("900.00"), expenseService.getAccounts(owner).get(0).balance());
		assertEquals(true, expenseRepository.findById(legacyExpense.getId()).orElseThrow().isBalanceAdjusted());
	}

	@Test
	void statementPreviewDetectsBankCategoryAndExistingDuplicates() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("HDFC Bank", new BigDecimal("1000.00")));
		CategoryDto coffee = expenseService.createCategory(owner, new CategoryRequest("Coffee"));
		expenseService.createCategory(owner, new CategoryRequest("Others"));
		LocalDate date = LocalDate.of(2026, 11, 20);
		expenseService.createExpense(owner, new ExpenseRequest(
				account.id(), coffee.id(), "UPI COFFEE DAY", new BigDecimal("250.00"), date));
		String csv = """
				HDFC Bank
				Transaction Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance
				20/11/2026,UPI COFFEE DAY,250.00,,750.00
				21/11/2026,Salary credit,,50000.00,50750.00
				""";
		MockMultipartFile file = new MockMultipartFile(
				"file", "hdfc-statement.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		StatementImportPreviewDto preview = statementImportService.preview(owner, file);

		assertEquals("HDFC Bank", preview.detectedBankName());
		assertEquals(account.id(), preview.suggestedAccountId());
		assertEquals(1, preview.skippedCredits());
		assertEquals(coffee.id(), preview.rows().get(0).suggestedCategoryId());
		assertEquals(true, preview.rows().get(0).duplicate());
		assertEquals(false, preview.rows().get(0).include());
	}

	@Test
	void offlineSnapshotContainsOnlyTheCurrentProfilesData() {
		AccountDto account = expenseService.createAccount(owner,
				new AccountRequest("Example Bank", new BigDecimal("1000.00")));
		CategoryDto category = expenseService.createCategory(owner, new CategoryRequest("Lunch"));
		expenseService.createExpense(owner, new ExpenseRequest(
				account.id(), category.id(), "Lunch", new BigDecimal("125.50"), LocalDate.now()));
		AppUser otherUser = userRepository.save(new AppUser("other-user", "test-password"));
		expenseService.createAccount(otherUser, new AccountRequest("Other Bank", new BigDecimal("200.00")));

		OfflineSnapshotDto snapshot = expenseService.getOfflineSnapshot(owner);

		assertEquals(1, snapshot.accounts().size());
		assertEquals(1, snapshot.expenses().size());
		assertEquals("Example Bank", snapshot.expenses().get(0).bankName());
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
