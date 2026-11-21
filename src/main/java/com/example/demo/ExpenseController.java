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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ExpenseController {

	private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ExpenseService expenseService;
	private final StatementImportService statementImportService;

	public ExpenseController(ExpenseService expenseService, StatementImportService statementImportService) {
		this.expenseService = expenseService;
		this.statementImportService = statementImportService;
	}

	@GetMapping("/dashboard")
	public DashboardDto dashboard(
			@AuthenticationPrincipal AppUser owner,
			@RequestParam(defaultValue = "month") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return expenseService.getDashboard(owner, period, date);
	}

	@GetMapping("/offline/snapshot")
	public OfflineSnapshotDto offlineSnapshot(@AuthenticationPrincipal AppUser owner) {
		return expenseService.getOfflineSnapshot(owner);
	}

	@GetMapping("/accounts")
	public List<AccountDto> accounts(@AuthenticationPrincipal AppUser owner) {
		return expenseService.getAccounts(owner);
	}

	@PostMapping("/accounts")
	public ResponseEntity<AccountDto> createAccount(
			@AuthenticationPrincipal AppUser owner, @Valid @RequestBody AccountRequest request) {
		return ResponseEntity.status(201).body(expenseService.createAccount(owner, request));
	}

	@DeleteMapping("/accounts/{accountId}")
	public ResponseEntity<Void> deleteAccount(
			@AuthenticationPrincipal AppUser owner, @PathVariable Long accountId) {
		expenseService.deleteAccount(owner, accountId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/categories")
	public List<CategoryDto> categories(@AuthenticationPrincipal AppUser owner) {
		return expenseService.getCategories(owner);
	}

	@PostMapping("/categories")
	public ResponseEntity<CategoryDto> createCategory(
			@AuthenticationPrincipal AppUser owner, @Valid @RequestBody CategoryRequest request) {
		return ResponseEntity.status(201).body(expenseService.createCategory(owner, request));
	}

	@GetMapping("/expenses")
	public List<ExpenseDto> expenses(
			@AuthenticationPrincipal AppUser owner,
			@RequestParam(required = false) Long accountId,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(defaultValue = "month") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return expenseService.getAllExpenses(owner, accountId, categoryId, period, date);
	}

	@PostMapping("/expenses")
	public ResponseEntity<ExpenseDto> createExpense(
			@AuthenticationPrincipal AppUser owner, @Valid @RequestBody ExpenseRequest request) {
		return ResponseEntity.status(201).body(expenseService.createExpense(owner, request));
	}

	@DeleteMapping("/expenses/{expenseId}")
	public ResponseEntity<Void> deleteExpense(
			@AuthenticationPrincipal AppUser owner, @PathVariable Long expenseId) {
		expenseService.deleteExpense(owner, expenseId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public StatementImportPreviewDto previewStatementImport(
			@AuthenticationPrincipal AppUser owner, @RequestParam("file") MultipartFile file) {
		return statementImportService.preview(owner, file);
	}

	@PostMapping("/imports")
	public StatementImportResult importStatementExpenses(
			@AuthenticationPrincipal AppUser owner, @Valid @RequestBody StatementImportRequest request) {
		return expenseService.importExpenses(owner, request);
	}

	@GetMapping("/accounts/{accountId}/export")
	public ResponseEntity<byte[]> exportAccountExpenses(
			@AuthenticationPrincipal AppUser owner,
			@PathVariable Long accountId,
			@RequestParam(defaultValue = "month") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
			throws IOException {
		List<ExpenseDto> expenses = expenseService.getExpenses(owner, accountId, null, period, date);
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
