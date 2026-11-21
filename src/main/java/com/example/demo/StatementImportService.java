package com.example.demo;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatementImportService {

	private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

	private final StatementFileParser fileParser;
	private final ExpenseService expenseService;
	private final BankAccountRepository accountRepository;
	private final ExpenseRepository expenseRepository;

	public StatementImportService(
			StatementFileParser fileParser,
			ExpenseService expenseService,
			BankAccountRepository accountRepository,
			ExpenseRepository expenseRepository) {
		this.fileParser = fileParser;
		this.expenseService = expenseService;
		this.accountRepository = accountRepository;
		this.expenseRepository = expenseRepository;
	}

	public StatementImportPreviewDto preview(AppUser owner, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw badRequest("Choose a non-empty statement file.");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw badRequest("The statement must be 5 MB or smaller.");
		}

		StatementFileParser.ParsedStatement statement;
		try {
			statement = fileParser.parse(file.getOriginalFilename(), file.getBytes());
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The statement file could not be read.", exception);
		}

		List<BankAccount> accounts = accountRepository.findByOwner_Id(owner.getId());
		List<BankAccount> matchingAccounts = statement.detectedBankName() == null
				? List.of()
				: accounts.stream()
						.filter(account -> matchesBank(account.getBankName(), statement.detectedBankName()))
						.toList();
		Long suggestedAccountId = matchingAccounts.size() == 1 ? matchingAccounts.get(0).getId() : null;
		String accountMessage = accountMatchMessage(statement.detectedBankName(), matchingAccounts);

		List<CategoryDto> categories = expenseService.getCategories(owner);
		Map<String, CategoryDto> categoriesByName = new HashMap<>();
		categories.forEach(category -> categoriesByName.put(category.name().toLowerCase(Locale.ROOT), category));
		CategoryDto others = categoriesByName.get("others");
		if (others == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The default Others category is missing.");
		}

		Map<String, Integer> existingTransactionCounts = suggestedAccountId == null
				? Map.of()
				: findExistingTransactionCounts(owner, suggestedAccountId, statement.transactions());
		List<StatementImportPreviewDto.Row> previewRows = new ArrayList<>();
		for (StatementFileParser.Transaction transaction : statement.transactions()) {
			String categoryName = suggestCategory(transaction.description());
			CategoryDto category = categoriesByName.getOrDefault(categoryName.toLowerCase(Locale.ROOT), others);
			String duplicateKey = transactionKey(
					transaction.expenseDate(), transaction.description(), transaction.amount());
			int existingCount = existingTransactionCounts.getOrDefault(duplicateKey, 0);
			boolean duplicate = existingCount > 0;
			if (duplicate) {
				existingTransactionCounts.put(duplicateKey, existingCount - 1);
			}
			previewRows.add(new StatementImportPreviewDto.Row(
					transaction.rowNumber(),
					transaction.expenseDate(),
					transaction.description(),
					transaction.amount(),
					category.id(),
					category.name(),
					"Others".equalsIgnoreCase(category.name()),
					transaction.assumedDebit(),
					duplicate,
					!duplicate));
		}

		return new StatementImportPreviewDto(
				statement.detectedBankName(),
				suggestedAccountId,
				accountMessage,
				statement.skippedCredits(),
				statement.skippedRows(),
				previewRows);
	}

	private Map<String, Integer> findExistingTransactionCounts(
			AppUser owner, Long accountId, List<StatementFileParser.Transaction> transactions) {
		LocalDate start = transactions.stream().map(StatementFileParser.Transaction::expenseDate)
				.min(Comparator.naturalOrder()).orElseThrow();
		LocalDate end = transactions.stream().map(StatementFileParser.Transaction::expenseDate)
				.max(Comparator.naturalOrder()).orElseThrow();
		Map<String, Integer> counts = new HashMap<>();
		expenseRepository.findByAccount_Owner_IdAndAccount_IdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
						owner.getId(), accountId, start, end)
				.forEach(expense -> counts.merge(
						transactionKey(expense.getExpenseDate(), expense.getDescription(), expense.getAmount()),
						1,
						Integer::sum));
		return counts;
	}

	private String accountMatchMessage(String bankName, List<BankAccount> matchingAccounts) {
		if (bankName == null) {
			return "Bank not detected. Choose the account for this statement.";
		}
		if (matchingAccounts.size() == 1) {
			return "Matched the only " + bankName + " account in this wallet.";
		}
		if (matchingAccounts.size() > 1) {
			return "More than one " + bankName + " account exists. Choose the correct one.";
		}
		return "No existing account matches " + bankName + ". Choose an account; imports do not create accounts.";
	}

	private boolean matchesBank(String accountName, String detectedBankName) {
		String account = accountName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		String detected = detectedBankName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		if (detected.startsWith("statebankofindia")) {
			return account.contains("sbi") || account.contains("statebankofindia");
		}
		if (detected.startsWith("bankofbaroda")) {
			return account.contains("bob") || account.contains("bankofbaroda");
		}
		if (detected.startsWith("punjabnationalbank")) {
			return account.contains("pnb") || account.contains("punjabnationalbank");
		}
		if (detected.startsWith("unionbankofindia")) {
			return account.contains("unionbank");
		}
		if (detected.startsWith("kotakmahindra")) {
			return account.contains("kotak");
		}
		if (detected.startsWith("idfcfirst")) {
			return account.contains("idfc");
		}
		if (detected.startsWith("bankofindia")) {
			return account.contains("bankofindia") || account.equals("boi");
		}
		if (detected.startsWith("bankofmaharashtra")) {
			return account.contains("bankofmaharashtra") || account.equals("bom");
		}
		String brand = detected.split("bank", 2)[0];
		return !brand.isBlank() && account.contains(brand);
	}

	private String suggestCategory(String description) {
		String value = description.toLowerCase(Locale.ROOT);
		if (containsAny(value, "recharge", "airtel", "jio", "vodafone", "bsnl", "vi recharge")) {
			return "Mobile Recharge";
		}
		if (containsAny(value, "uber", "ola", "rapido", "metro", "irctc", "petrol", "fuel", "bus ticket",
				"train ticket", "parking", "toll")) {
			return "Transport";
		}
		if (containsAny(value, "pharmacy", "chemist", "medicine", "medplus", "netmeds", "apollo pharmacy",
				"1mg", "tata 1mg")) {
			return "Medicine";
		}
		if (containsAny(value, "fruit", "fruits", "fruit market")) {
			return "Fruits";
		}
		if (containsAny(value, "grocery", "groceries", "supermarket", "bigbasket", "blinkit", "zepto",
				"instamart", "dmart", "reliance fresh")) {
			return "Groceries";
		}
		if (containsAny(value, "myntra", "clothing", "apparel", "fashion", "garment", "dress")) {
			return "Clothes";
		}
		if (containsAny(value, "netflix", "prime video", "hotstar", "bookmyshow", "cinema", "movie",
				"spotify", "disney+", "entertainment")) {
			return "Entertainment";
		}
		if (containsAny(value, "decathlon", "sports store", "gym membership", "sports equipment")) {
			return "Sports";
		}
		if (containsAny(value, "coffee", "cafe coffee day", "barista", "starbucks")) {
			return "Coffee";
		}
		if (containsAny(value, "tea", "chai", "tea stall")) {
			return "Tea";
		}
		if (containsAny(value, "breakfast")) {
			return "Breakfast";
		}
		if (containsAny(value, "lunch")) {
			return "Lunch";
		}
		if (containsAny(value, "dinner")) {
			return "Dinner";
		}
		if (containsAny(value, "snack", "chips", "biscuit", "namkeen", "bakery")) {
			return "Snacks";
		}
		return "Others";
	}

	private boolean containsAny(String value, String... keywords) {
		for (String keyword : keywords) {
			if (keyword.length() <= 4) {
				if (java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(keyword)
								+ "(?![a-z0-9])")
						.matcher(value).find()) {
					return true;
				}
			} else if (value.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private String transactionKey(LocalDate date, String description, BigDecimal amount) {
		String normalizedDescription = description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
		return date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + normalizedDescription;
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
