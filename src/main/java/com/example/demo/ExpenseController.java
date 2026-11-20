package com.example.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ExpenseController {

	private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ExpenseService expenseService;

	public ExpenseController(ExpenseService expenseService) {
		this.expenseService = expenseService;
	}

	@GetMapping("/dashboard")
	public DashboardDto dashboard(
			@RequestParam(defaultValue = "month") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return expenseService.getDashboard(period, date);
	}

	@GetMapping("/accounts")
	public List<AccountDto> accounts() {
		return expenseService.getAccounts();
	}

	@PostMapping("/accounts")
	public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody AccountRequest request) {
		return ResponseEntity.status(201).body(expenseService.createAccount(request));
	}

	@DeleteMapping("/accounts/{accountId}")
	public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
		expenseService.deleteAccount(accountId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/categories")
	public List<CategoryDto> categories() {
		return expenseService.getCategories();
	}

	@PostMapping("/categories")
	public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryRequest request) {
		return ResponseEntity.status(201).body(expenseService.createCategory(request));
	}

	@GetMapping("/expenses")
	public List<ExpenseDto> expenses(
			@RequestParam(required = false) Long accountId,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(defaultValue = "month") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return expenseService.getAllExpenses(accountId, categoryId, period, date);
	}

	@PostMapping("/expenses")
	public ResponseEntity<ExpenseDto> createExpense(@Valid @RequestBody ExpenseRequest request) {
		return ResponseEntity.status(201).body(expenseService.createExpense(request));
	}

	@DeleteMapping("/expenses/{expenseId}")
	public ResponseEntity<Void> deleteExpense(@PathVariable Long expenseId) {
		expenseService.deleteExpense(expenseId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/accounts/{accountId}/export")
	public ResponseEntity<byte[]> exportAccountExpenses(
			@PathVariable Long accountId,
			@RequestParam(defaultValue = "month") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
			throws IOException {
		List<ExpenseDto> expenses = expenseService.getExpenses(accountId, null, period, date);
		byte[] workbook = createWorkbook(expenses);
		String fileName = "expenses-account-" + accountId + "-" + period.toLowerCase() + ".xlsx";
		return ResponseEntity.ok()
				.contentType(XLSX_MEDIA_TYPE)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.body(workbook);
	}

	private byte[] createWorkbook(List<ExpenseDto> expenses) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Expenses");
			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("Date");
			header.createCell(1).setCellValue("Bank");
			header.createCell(2).setCellValue("Category");
			header.createCell(3).setCellValue("Description");
			header.createCell(4).setCellValue("Amount (INR)");

			int rowIndex = 1;
			for (ExpenseDto expense : expenses) {
				Row row = sheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(expense.expenseDate().toString());
				row.createCell(1).setCellValue(expense.bankName());
				row.createCell(2).setCellValue(expense.categoryName());
				row.createCell(3).setCellValue(expense.description());
				row.createCell(4).setCellValue(expense.amount().doubleValue());
			}
			for (int columnIndex = 0; columnIndex < 5; columnIndex++) {
				sheet.autoSizeColumn(columnIndex);
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}
}
