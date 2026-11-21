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
	private final AppUserRepository userRepository;

	public ExpenseService(BankAccountRepository accountRepository, ExpenseCategoryRepository categoryRepository,
			ExpenseRepository expenseRepository, AppUserRepository userRepository) {
		this.accountRepository = accountRepository;
		this.categoryRepository = categoryRepository;
		this.expenseRepository = expenseRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<AccountDto> getAccounts(AppUser owner) {
		return accountRepository.findByOwner_Id(owner.getId()).stream().map(AccountDto::from).toList();
	}

	@Transactional
	public AccountDto createAccount(AppUser owner, AccountRequest request) {
		BankAccount account = accountRepository.save(
				new BankAccount(request.bankName().trim(), request.balance(),
						userRepository.getReferenceById(owner.getId())));
		return AccountDto.from(account);
	}

	@Transactional
	public void deleteAccount(AppUser owner, Long accountId) {
		BankAccount account = requireAccount(owner.getId(), accountId);
		expenseRepository.deleteByAccount_Id(accountId);
		accountRepository.delete(account);
	}

	@Transactional(readOnly = true)
	public List<CategoryDto> getCategories(AppUser owner) {
		List<ExpenseCategory> categories = new ArrayList<>(categoryRepository.findByOwnerIsNull());
		categories.addAll(categoryRepository.findByOwner_Id(owner.getId()));
		return categories.stream()
				.sorted(Comparator.comparing(ExpenseCategory::getName, String.CASE_INSENSITIVE_ORDER))
				.map(CategoryDto::from)
				.toList();
	}

	@Transactional
	public CategoryDto createCategory(AppUser owner, CategoryRequest request) {
		String name = request.name().trim();
		if (categoryRepository.existsByNameIgnoreCase(name)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That category already exists");
		}
		return CategoryDto.from(categoryRepository.save(new ExpenseCategory(
				name, userRepository.getReferenceById(owner.getId()))));
	}

	@Transactional
	public ExpenseDto createExpense(AppUser owner, ExpenseRequest request) {
		if (request.clientRequestId() != null) {
			Expense existing = expenseRepository
					.findByAccount_Owner_IdAndAccount_IdAndClientRequestId(
							owner.getId(), request.accountId(), request.clientRequestId())
					.orElse(null);
			if (existing != null) {
				return ExpenseDto.from(existing);
			}
		}
		BankAccount account = requireAccount(owner.getId(), request.accountId());
		ExpenseCategory category = categoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
		if (category.getOwner() != null && !category.getOwner().getId().equals(owner.getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
		}
		String description = request.description() == null || request.description().isBlank()
				? category.getName()
				: request.description().trim();

		account.setBalance(account.getBalance().subtract(request.amount()));
		Expense expense = expenseRepository.save(new Expense(
				account, category, description, request.amount(), request.expenseDate(), request.clientRequestId()));
		return ExpenseDto.from(expense);
	}

	@Transactional
	public StatementImportResult importExpenses(AppUser owner, StatementImportRequest request) {
		BankAccount account = requireAccount(owner.getId(), request.accountId());
		int imported = 0;
		int alreadyImported = 0;
		for (StatementImportRequest.Row row : request.rows()) {
			Expense existing = expenseRepository
					.findByAccount_Owner_IdAndAccount_IdAndClientRequestId(
							owner.getId(), account.getId(), row.clientRequestId())
					.orElse(null);
			if (existing != null) {
				alreadyImported++;
				continue;
			}
			ExpenseCategory category = categoryRepository.findById(row.categoryId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
			if (category.getOwner() != null && !category.getOwner().getId().equals(owner.getId())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
			}
			expenseRepository.save(new Expense(
					account, category, row.description().trim(), row.amount(), row.expenseDate(),
					row.clientRequestId(), false));
			imported++;
		}
		return new StatementImportResult(imported, alreadyImported);
	}

	@Transactional
	public void deleteExpense(AppUser owner, Long expenseId) {
		Expense expense = expenseRepository.findByIdAndAccount_Owner_Id(expenseId, owner.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
		BankAccount account = expense.getAccount();
		if (expense.isBalanceAdjusted()) {
			account.setBalance(account.getBalance().add(expense.getAmount()));
		}
		expenseRepository.delete(expense);
	}

	@Transactional(readOnly = true)
	public List<ExpenseDto> getExpenses(
			AppUser owner, Long accountId, Long categoryId, String period, LocalDate date) {
		requireAccount(owner.getId(), accountId);
		PeriodRange range = PeriodRange.from(period, date);
		List<Expense> expenses = categoryId == null
				? expenseRepository.findByAccount_Owner_IdAndAccount_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						owner.getId(), accountId, range.startDate(), range.endDate())
				: expenseRepository.findByAccount_Owner_IdAndAccount_IdAndCategory_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						owner.getId(), accountId, categoryId, range.startDate(), range.endDate());
		return expenses.stream().map(ExpenseDto::from).toList();
	}

	@Transactional(readOnly = true)
	public List<ExpenseDto> getAllExpenses(
			AppUser owner, Long accountId, Long categoryId, String period, LocalDate date) {
		if (accountId != null) {
			return getExpenses(owner, accountId, categoryId, period, date);
		}
		PeriodRange range = PeriodRange.from(period, date);
		List<Expense> expenses = categoryId == null
				? expenseRepository.findByAccount_Owner_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						owner.getId(), range.startDate(), range.endDate())
				: expenseRepository.findByAccount_Owner_IdAndCategory_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						owner.getId(), categoryId, range.startDate(), range.endDate());
		return expenses.stream().map(ExpenseDto::from).toList();
	}

	@Transactional(readOnly = true)
	public DashboardDto getDashboard(AppUser owner, String period, LocalDate date) {
		PeriodRange range = PeriodRange.from(period, date);
		List<BankAccount> accounts = accountRepository.findByOwner_Id(owner.getId());
		List<Expense> expenses = expenseRepository.findByAccount_Owner_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
				owner.getId(), range.startDate(), range.endDate());
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

		return new DashboardDto(totalSpent, summaries, getCategories(owner));
	}

	@Transactional(readOnly = true)
	public OfflineSnapshotDto getOfflineSnapshot(AppUser owner) {
		List<AccountDto> accounts = getAccounts(owner);
		List<CategoryDto> categories = getCategories(owner);
		List<ExpenseDto> expenses = expenseRepository.findByAccount_Owner_IdOrderByExpenseDateDescIdDesc(owner.getId())
				.stream().map(ExpenseDto::from).toList();
		return new OfflineSnapshotDto(accounts, categories, expenses);
	}

	private BankAccount requireAccount(Long ownerId, Long accountId) {
		return accountRepository.findByIdAndOwner_Id(accountId, ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));
	}
}
