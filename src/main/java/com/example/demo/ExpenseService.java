package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExpenseService {

	private final BankAccountRepository accountRepository;
	private final ExpenseCategoryRepository categoryRepository;
	private final ExpenseRepository expenseRepository;

	public ExpenseService(BankAccountRepository accountRepository, ExpenseCategoryRepository categoryRepository,
			ExpenseRepository expenseRepository) {
		this.accountRepository = accountRepository;
		this.categoryRepository = categoryRepository;
		this.expenseRepository = expenseRepository;
	}

	@Transactional(readOnly = true)
	public List<AccountDto> getAccounts() {
		return accountRepository.findAll().stream().map(AccountDto::from).toList();
	}

	@Transactional
	public AccountDto createAccount(AccountRequest request) {
		BankAccount account = accountRepository.save(
				new BankAccount(request.bankName().trim(), request.balance()));
		return AccountDto.from(account);
	}

	@Transactional
	public void deleteAccount(Long accountId) {
		BankAccount account = requireAccount(accountId);
		expenseRepository.deleteByAccountId(accountId);
		accountRepository.delete(account);
	}

	@Transactional(readOnly = true)
	public List<CategoryDto> getCategories() {
		return categoryRepository.findAll().stream()
				.sorted(Comparator.comparing(ExpenseCategory::getName, String.CASE_INSENSITIVE_ORDER))
				.map(CategoryDto::from)
				.toList();
	}

	@Transactional
	public CategoryDto createCategory(CategoryRequest request) {
		String name = request.name().trim();
		if (categoryRepository.existsByNameIgnoreCase(name)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That category already exists");
		}
		return CategoryDto.from(categoryRepository.save(new ExpenseCategory(name)));
	}

	@Transactional
	public ExpenseDto createExpense(ExpenseRequest request) {
		BankAccount account = accountRepository.findById(request.accountId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));
		ExpenseCategory category = categoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
		String description = request.description() == null || request.description().isBlank()
				? category.getName()
				: request.description().trim();

		account.setBalance(account.getBalance().subtract(request.amount()));
		Expense expense = expenseRepository.save(new Expense(
				account, category, description, request.amount(), request.expenseDate()));
		return ExpenseDto.from(expense);
	}

	@Transactional
	public void deleteExpense(Long expenseId) {
		Expense expense = expenseRepository.findById(expenseId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
		BankAccount account = expense.getAccount();
		account.setBalance(account.getBalance().add(expense.getAmount()));
		expenseRepository.delete(expense);
	}

	@Transactional(readOnly = true)
	public List<ExpenseDto> getExpenses(
			Long accountId, Long categoryId, String period, LocalDate date) {
		requireAccount(accountId);
		PeriodRange range = PeriodRange.from(period, date);
		List<Expense> expenses = categoryId == null
				? expenseRepository.findByAccountIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						accountId, range.startDate(), range.endDate())
				: expenseRepository.findByAccountIdAndCategoryIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						accountId, categoryId, range.startDate(), range.endDate());
		return expenses.stream().map(ExpenseDto::from).toList();
	}

	@Transactional(readOnly = true)
	public List<ExpenseDto> getAllExpenses(Long accountId, Long categoryId, String period, LocalDate date) {
		if (accountId != null) {
			return getExpenses(accountId, categoryId, period, date);
		}
		PeriodRange range = PeriodRange.from(period, date);
		List<Expense> expenses = categoryId == null
				? expenseRepository.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						range.startDate(), range.endDate())
				: expenseRepository.findByCategoryIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						categoryId, range.startDate(), range.endDate());
		return expenses.stream().map(ExpenseDto::from).toList();
	}

	@Transactional(readOnly = true)
	public DashboardDto getDashboard(String period, LocalDate date) {
		PeriodRange range = PeriodRange.from(period, date);
		List<BankAccount> accounts = accountRepository.findAll();
		List<Expense> expenses = expenseRepository.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(
				range.startDate(), range.endDate());
		Map<Long, List<Expense>> expensesByAccount = new LinkedHashMap<>();
		for (Expense expense : expenses) {
			expensesByAccount.computeIfAbsent(expense.getAccount().getId(), ignored -> new ArrayList<>()).add(expense);
		}

		BigDecimal totalSpent = expenses.stream()
				.map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

		List<AccountSummaryDto> summaries = accounts.stream().map(account -> {
			List<Expense> accountExpenses = expensesByAccount.getOrDefault(account.getId(), List.of());
			Map<Long, BigDecimal> categoryTotals = new LinkedHashMap<>();
			Map<Long, String> categoryNames = new LinkedHashMap<>();
			for (Expense expense : accountExpenses) {
				Long categoryId = expense.getCategory().getId();
				categoryTotals.merge(categoryId, expense.getAmount(), BigDecimal::add);
				categoryNames.put(categoryId, expense.getCategory().getName());
			}
			List<CategorySpendDto> breakdown = categoryTotals.entrySet().stream()
					.map(entry -> new CategorySpendDto(entry.getKey(), categoryNames.get(entry.getKey()), entry.getValue()))
					.sorted(Comparator.comparing(CategorySpendDto::amount).reversed())
					.toList();
			List<ExpenseDto> recent = accountExpenses.stream().limit(5).map(ExpenseDto::from).toList();
			BigDecimal spent = accountExpenses.stream()
					.map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
			return new AccountSummaryDto(
					account.getId(), account.getBankName(), account.getBalance(), spent, breakdown, recent);
		}).toList();

		return new DashboardDto(totalSpent, summaries, getCategories());
	}

	private BankAccount requireAccount(Long accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));
	}
}
