package com.example.demo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StatementFileParser {

	private static final int MAX_ROWS = 2000;
	private static final List<BankAlias> BANKS = List.of(
			new BankAlias("State Bank of India", List.of("state bank of india", "sbi")),
			new BankAlias("Bank of Baroda", List.of("bank of baroda", "bob")),
			new BankAlias("Kotak Mahindra Bank", List.of("kotak mahindra", "kotak")),
			new BankAlias("IDFC FIRST Bank", List.of("idfc first", "idfc")),
			new BankAlias("Punjab National Bank", List.of("punjab national bank", "pnb")),
			new BankAlias("Canara Bank", List.of("canara bank", "canara")),
			new BankAlias("Union Bank of India", List.of("union bank of india", "union bank")),
			new BankAlias("IndusInd Bank", List.of("indusind bank", "indusind")),
			new BankAlias("Federal Bank", List.of("federal bank", "federal")),
			new BankAlias("HDFC Bank", List.of("hdfc bank", "hdfc")),
			new BankAlias("ICICI Bank", List.of("icici bank", "icici")),
			new BankAlias("Axis Bank", List.of("axis bank", "axis")),
			new BankAlias("Yes Bank", List.of("yes bank")),
			new BankAlias("Bank of India", List.of("bank of india")),
			new BankAlias("Bank of Maharashtra", List.of("bank of maharashtra")));
	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/uuuu").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-M-uuuu").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("uuuu-M-d").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-uu").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/uuuu").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/uu").toFormatter(Locale.ROOT),
			new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/uu").toFormatter(Locale.ROOT));
	private static final String MONTH_NAMES =
			"(?:January|February|March|April|May|June|July|August|September|October|November|December"
					+ "|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)";
	private static final Pattern TEXT_DATE_PREFIX = Pattern.compile(
			"(?i)^(?:(\\d{1,2})(?:st|nd|rd|th)?\\s*(" + MONTH_NAMES + ")\\.?|("
					+ MONTH_NAMES + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?)(?:\\s*,?\\s*(\\d{4}))?"
					+ "(?:\\s*[-:|]\\s*|\\s+|$)(.*)$");
	private static final Pattern LEDGER_AMOUNT_AT_END = Pattern.compile(
			"(?i)^(.+?)\\s+(\\d[\\d,]*(?:\\.\\d{1,2})?"
					+ "(?:\\s*\\+\\s*\\d[\\d,]*(?:\\.\\d{1,2})?)*)\\s*(?:rs\\.?)?$");
	private static final Pattern LEDGER_AMOUNT_AT_START = Pattern.compile(
			"(?i)^(?:rs\\.?\\s*)?(\\d[\\d,]*(?:\\.\\d{1,2})?)(?:\\s*rs\\.?)?\\s+(.+)$");

	public ParsedStatement parse(String fileName, byte[] content) {
		if (content == null || content.length == 0) {
			throw badRequest("Choose a non-empty statement file.");
		}
		String extension = fileExtension(fileName);
		if ("pdf".equals(extension)) {
			String text = extractPdfText(content);
			if (text.isBlank()) {
				throw badRequest(
						"This PDF has no selectable text. Scanned PDFs require OCR; export the statement as CSV or Excel.");
			}
			return parse(fileName + ".txt", text.getBytes(StandardCharsets.UTF_8));
		}
		if ("txt".equals(extension)) {
			String text = decodeText(content);
			if (!hasDelimitedTable(text) && isDateGroupedLedger(text)) {
				return parseDateGroupedLedger(fileName, text);
			}
		}
		List<List<String>> table;
		if ("xlsx".equals(extension)) {
			table = readWorkbook(content);
		} else if ("csv".equals(extension) || "txt".equals(extension)) {
			table = readDelimitedText(content, extension);
		} else {
			throw badRequest("Use a CSV, Excel (.xlsx), PDF, or text (.txt) file.");
		}
		if (table.size() > MAX_ROWS + 30) {
			throw badRequest("The statement is too large. Import up to 2,000 transactions at a time.");
		}

		Header header = findHeader(table);
		int dataStart = header == null ? 0 : header.rowIndex() + 1;
		String bankName = detectBank(fileName, table);
		List<Transaction> transactions = new ArrayList<>();
		int skippedCredits = 0;
		int skippedRows = 0;

		for (int index = dataStart; index < table.size(); index++) {
			List<String> row = table.get(index);
			ParsedRow parsed = header == null ? parseUnheadedRow(row) : parseRow(row, header);
			if (parsed == null) {
				if (header != null || row.stream().anyMatch(cell -> parseDate(cell) != null)) {
					skippedRows++;
				}
				continue;
			}
			if (parsed.credit()) {
				skippedCredits++;
				continue;
			}
			if (parsed.transaction() == null) {
				skippedRows++;
				continue;
			}
			Transaction transaction = parsed.transaction();
			transactions.add(new Transaction(
					index + 1,
					transaction.expenseDate(),
					transaction.description().substring(0, Math.min(120, transaction.description().length())),
					transaction.amount(),
					transaction.assumedDebit()));
			if (transactions.size() > MAX_ROWS) {
				throw badRequest("The statement has more than 2,000 expense rows. Import a smaller date range.");
			}
		}
		if (transactions.isEmpty()) {
			throw badRequest("No expense rows could be read. Use a statement with date, description, and debit amount columns.");
		}
		return new ParsedStatement(bankName, skippedCredits, skippedRows, transactions);
	}

	private String extractPdfText(byte[] content) {
		try (PDDocument document = Loader.loadPDF(content)) {
			return new PDFTextStripper().getText(document);
		} catch (InvalidPasswordException exception) {
			throw badRequest("Password-protected PDFs are not supported. Remove the password and try again.");
		} catch (IOException exception) {
			throw badRequest("The PDF file could not be read.");
		}
	}

	private List<List<String>> readWorkbook(byte[] content) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			if (workbook.getNumberOfSheets() == 0) {
				throw badRequest("The Excel file does not contain a worksheet.");
			}
			DataFormatter formatter = new DataFormatter(Locale.ROOT);
			List<List<String>> rows = new ArrayList<>();
			for (Row row : workbook.getSheetAt(0)) {
				List<String> values = new ArrayList<>();
				for (int column = 0; column < row.getLastCellNum(); column++) {
					values.add(formatter.formatCellValue(row.getCell(column)).trim());
				}
				if (values.stream().anyMatch(value -> !value.isBlank())) {
					rows.add(values);
				}
			}
			return rows;
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Excel file could not be read.", exception);
		}
	}

	private List<List<String>> readDelimitedText(byte[] content, String extension) {
		String text = decodeText(content);
		if (text.isBlank()) {
			throw badRequest("The statement file is empty.");
		}
		char delimiter = detectDelimiter(text);
		if (delimiter == '\0') {
			if (!"txt".equals(extension)) {
				throw badRequest("The CSV file must contain comma-, semicolon-, tab-, or pipe-separated columns.");
			}
			return text.lines().map(String::trim).filter(line -> !line.isBlank())
					.map(line -> List.of(line.split("\\s{2,}", -1))).toList();
		}
		return parseDelimited(text, delimiter);
	}

	private String decodeText(byte[] content) {
		String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
		return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
	}

	private boolean hasDelimitedTable(String text) {
		for (char delimiter : new char[] { ',', '\t', ';', '|' }) {
			int rowsWithColumns = 0;
			for (String line : text.lines().toList()) {
				if (!line.isBlank() && countUnquoted(line, delimiter) >= 2 && ++rowsWithColumns >= 2) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isDateGroupedLedger(String text) {
		LocalDate currentDate = null;
		int currentYear = LocalDate.now().getYear();
		for (String line : text.lines().map(String::trim).toList()) {
			if (line.isBlank() || isBalanceLine(line)) {
				continue;
			}
			DatePrefix datePrefix = parseTextDatePrefix(line, currentYear);
			if (datePrefix != null) {
				currentDate = datePrefix.date();
				ParsedRow parsed = parseLedgerLine(datePrefix.remainder(), currentDate, 0);
				if (parsed.transaction() != null) {
					return true;
				}
			} else if (currentDate != null && parseLedgerLine(line, currentDate, 0).transaction() != null) {
				return true;
			}
		}
		return false;
	}

	private ParsedStatement parseDateGroupedLedger(String fileName, String text) {
		int currentYear = LocalDate.now().getYear();
		LocalDate currentDate = null;
		int skippedCredits = 0;
		int skippedRows = 0;
		List<Transaction> transactions = new ArrayList<>();

		int rowNumber = 0;
		for (String rawLine : text.lines().toList()) {
			rowNumber++;
			String line = rawLine.trim();
			if (line.isBlank() || isBalanceLine(line)) {
				continue;
			}
			DatePrefix datePrefix = parseTextDatePrefix(line, currentYear);
			if (datePrefix != null) {
				currentDate = datePrefix.date();
				line = datePrefix.remainder();
				if (line.isBlank()) {
					continue;
				}
			}
			if (currentDate == null) {
				continue;
			}

			ParsedRow parsed = parseLedgerLine(line, currentDate, rowNumber);
			if (parsed.credit()) {
				skippedCredits++;
			} else if (parsed.transaction() != null) {
				Transaction transaction = parsed.transaction();
				transactions.add(new Transaction(
						transaction.rowNumber(),
						transaction.expenseDate(),
						transaction.description().substring(0, Math.min(120, transaction.description().length())),
						transaction.amount(),
						true));
				if (transactions.size() > MAX_ROWS) {
					throw badRequest("The statement has more than 2,000 expense rows. Import a smaller date range.");
				}
			} else if (line.matches(".*\\d.*")) {
				skippedRows++;
			}
		}

		if (transactions.isEmpty()) {
			throw badRequest("No expense rows could be read from the date-grouped text.");
		}
		return new ParsedStatement(detectBank(fileName, List.of()), skippedCredits, skippedRows, transactions);
	}

	private DatePrefix parseTextDatePrefix(String line, int defaultYear) {
		Matcher matcher = TEXT_DATE_PREFIX.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		String monthText = matcher.group(1) == null ? matcher.group(3) : matcher.group(2);
		String dayText = matcher.group(1) == null ? matcher.group(4) : matcher.group(1);
		Month month = parseMonth(monthText);
		if (month == null) {
			return null;
		}
		int year = matcher.group(5) == null ? defaultYear : Integer.parseInt(matcher.group(5));
		try {
			return new DatePrefix(
					LocalDate.of(year, month, Integer.parseInt(dayText)),
					matcher.group(6).trim());
		} catch (DateTimeException exception) {
			return null;
		}
	}

	private Month parseMonth(String value) {
		String month = value.replace(".", "");
		for (String pattern : List.of("MMMM", "MMM")) {
			try {
				return Month.from(new DateTimeFormatterBuilder()
						.parseCaseInsensitive()
						.appendPattern(pattern)
						.toFormatter(Locale.ENGLISH)
						.parse(month));
			} catch (DateTimeParseException ignored) {
				// Try the abbreviated month format.
			}
		}
		return null;
	}

	private ParsedRow parseLedgerLine(String line, LocalDate date, int rowNumber) {
		if (line.isBlank()) {
			return ParsedRow.skippedRow();
		}
		if (isCredit(line.toLowerCase(Locale.ROOT))) {
			return ParsedRow.creditRow();
		}

		Matcher trailingAmount = LEDGER_AMOUNT_AT_END.matcher(line);
		String description;
		String amounts;
		if (trailingAmount.matches()) {
			description = trailingAmount.group(1).trim();
			amounts = trailingAmount.group(2);
		} else {
			Matcher leadingAmount = LEDGER_AMOUNT_AT_START.matcher(line);
			if (!leadingAmount.matches()) {
				return ParsedRow.skippedRow();
			}
			amounts = leadingAmount.group(1);
			description = leadingAmount.group(2).trim();
		}
		BigDecimal amount = sumAmounts(amounts);
		if (description.isBlank() || amount == null || amount.signum() <= 0) {
			return ParsedRow.skippedRow();
		}
		return ParsedRow.transaction(new Transaction(rowNumber, date, description, amount, true));
	}

	private BigDecimal sumAmounts(String value) {
		BigDecimal total = BigDecimal.ZERO;
		for (String part : value.split("\\s*\\+\\s*")) {
			BigDecimal amount = parseAmount(part);
			if (amount == null || amount.signum() <= 0) {
				return null;
			}
			total = total.add(amount);
		}
		return total;
	}

	private boolean isBalanceLine(String line) {
		String value = line.toLowerCase(Locale.ROOT);
		return value.startsWith("current balance")
				|| value.startsWith("opening balance")
				|| value.startsWith("closing balance")
				|| value.startsWith("available balance");
	}

	private char detectDelimiter(String text) {
		char selected = '\0';
		int greatestCount = 0;
		int linesChecked = 0;
		for (String line : text.lines().toList()) {
			if (line.isBlank()) {
				continue;
			}
			for (char candidate : new char[] { ',', '\t', ';', '|' }) {
				int count = countUnquoted(line, candidate);
				if (count > greatestCount) {
					greatestCount = count;
					selected = candidate;
				}
			}
			if (++linesChecked == 30) {
				break;
			}
		}
		return selected;
	}

	private int countUnquoted(String line, char delimiter) {
		int count = 0;
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character == '"') {
				if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
					index++;
				} else {
					quoted = !quoted;
				}
			} else if (character == delimiter && !quoted) {
				count++;
			}
		}
		return count;
	}

	private List<List<String>> parseDelimited(String text, char delimiter) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character == '"') {
				if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
					cell.append('"');
					index++;
				} else {
					quoted = !quoted;
				}
			} else if (character == delimiter && !quoted) {
				row.add(cell.toString().trim());
				cell.setLength(0);
			} else if ((character == '\n' || character == '\r') && !quoted) {
				if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
					index++;
				}
				row.add(cell.toString().trim());
				cell.setLength(0);
				if (row.stream().anyMatch(value -> !value.isBlank())) {
					rows.add(row);
				}
				row = new ArrayList<>();
			} else {
				cell.append(character);
			}
		}
		if (quoted) {
			throw badRequest("The statement contains an unclosed quoted field.");
		}
		row.add(cell.toString().trim());
		if (row.stream().anyMatch(value -> !value.isBlank())) {
			rows.add(row);
		}
		return rows;
	}

	private Header findHeader(List<List<String>> rows) {
		for (int index = 0; index < Math.min(rows.size(), 30); index++) {
			List<String> cells = rows.get(index);
			Header header = new Header(
					index,
					findDateColumn(cells),
					findDescriptionColumn(cells),
					findDebitColumn(cells),
					findCreditColumn(cells),
					findAmountColumn(cells),
					findTypeColumn(cells));
			if (header.dateColumn() >= 0 && header.descriptionColumn() >= 0
					&& (header.debitColumn() >= 0 || header.amountColumn() >= 0)) {
				return header;
			}
		}
		return null;
	}

	private int findDateColumn(List<String> cells) {
		int fallback = -1;
		for (int index = 0; index < cells.size(); index++) {
			String header = normalizeHeader(cells.get(index));
			if (header.contains("transaction date") || header.equals("txn date") || header.equals("trans date")) {
				return index;
			}
			if (fallback < 0 && header.contains("date")) {
				fallback = index;
			}
		}
		return fallback;
	}

	private int findDescriptionColumn(List<String> cells) {
		for (String preferred : List.of("narration", "particulars", "description", "transaction details", "remarks")) {
			for (int index = 0; index < cells.size(); index++) {
				if (normalizeHeader(cells.get(index)).contains(preferred)) {
					return index;
				}
			}
		}
		return -1;
	}

	private int findDebitColumn(List<String> cells) {
		for (int index = 0; index < cells.size(); index++) {
			String header = normalizeHeader(cells.get(index));
			if (header.contains("debit") || header.contains("withdrawal") || header.contains("withdrawn")
					|| header.contains("paid out")) {
				return index;
			}
		}
		return -1;
	}

	private int findCreditColumn(List<String> cells) {
		for (int index = 0; index < cells.size(); index++) {
			String header = normalizeHeader(cells.get(index));
			if (header.contains("credit") || header.contains("deposit") || header.contains("paid in")) {
				return index;
			}
		}
		return -1;
	}

	private int findAmountColumn(List<String> cells) {
		for (int index = 0; index < cells.size(); index++) {
			String header = normalizeHeader(cells.get(index));
			if ((header.contains("amount") || header.equals("amt"))
					&& !header.contains("balance") && !header.contains("debit") && !header.contains("credit")) {
				return index;
			}
		}
		return -1;
	}

	private int findTypeColumn(List<String> cells) {
		for (int index = 0; index < cells.size(); index++) {
			String header = normalizeHeader(cells.get(index));
			if (header.equals("type") || header.contains("dr cr") || header.contains("credit debit")
					|| header.contains("transaction type")) {
				return index;
			}
		}
		return -1;
	}

	private ParsedRow parseRow(List<String> row, Header header) {
		LocalDate date = parseDate(cell(row, header.dateColumn()));
		String description = cell(row, header.descriptionColumn());
		if (date == null || description.isBlank()) {
			return null;
		}
		String type = cell(row, header.typeColumn()).toLowerCase(Locale.ROOT);
		if (isCredit(type)) {
			return ParsedRow.creditRow();
		}

		BigDecimal amount = null;
		boolean assumedDebit = false;
		if (header.debitColumn() >= 0) {
			amount = parseAmount(cell(row, header.debitColumn()));
			if (amount == null && header.amountColumn() >= 0 && isDebit(type)) {
				amount = parseAmount(cell(row, header.amountColumn()));
			}
			if (amount == null || amount.signum() == 0) {
				if (header.creditColumn() >= 0 && parseAmount(cell(row, header.creditColumn())) != null) {
					return ParsedRow.creditRow();
				}
				return null;
			}
		} else {
			amount = parseAmount(cell(row, header.amountColumn()));
			if (amount == null || amount.signum() == 0) {
				return null;
			}
			assumedDebit = !isDebit(type) && !amountIsNegative(cell(row, header.amountColumn()));
		}
		return ParsedRow.transaction(new Transaction(0, date, description, amount.abs(), assumedDebit));
	}

	private ParsedRow parseUnheadedRow(List<String> row) {
		if (row.size() < 3) {
			return null;
		}
		int dateIndex = -1;
		LocalDate date = null;
		for (int index = 0; index < row.size(); index++) {
			date = parseDate(row.get(index));
			if (date != null) {
				dateIndex = index;
				break;
			}
		}
		if (dateIndex < 0) {
			return null;
		}
		int amountIndex = -1;
		BigDecimal amount = null;
		for (int index = dateIndex + 1; index < row.size(); index++) {
			BigDecimal candidate = parseAmount(row.get(index));
			if (candidate != null) {
				amountIndex = index;
				amount = candidate;
				break;
			}
		}
		if (amountIndex < 0 || amount == null || amount.signum() == 0) {
			return null;
		}
		String description = String.join(" ", row.subList(dateIndex + 1, amountIndex)).trim();
		if (description.isBlank()) {
			return null;
		}
		String suffix = String.join(" ", row.subList(amountIndex + 1, row.size())).toLowerCase(Locale.ROOT);
		if (isCredit(suffix)) {
			return ParsedRow.creditRow();
		}
		return ParsedRow.transaction(new Transaction(0, date, description, amount.abs(), !amountIsNegative(row.get(amountIndex))));
	}

	private LocalDate parseDate(String value) {
		String candidate = value.trim().replace('T', ' ');
		int space = candidate.indexOf(' ');
		if (space > 0) {
			candidate = candidate.substring(0, space);
		}
		for (DateTimeFormatter format : DATE_FORMATS) {
			try {
				return LocalDate.parse(candidate, format);
			} catch (DateTimeParseException ignored) {
				// Try the remaining common statement date formats.
			}
		}
		return null;
	}

	private BigDecimal parseAmount(String value) {
		if (value == null || value.isBlank() || value.equals("-")) {
			return null;
		}
		String normalized = value.trim().replaceAll("(?i)\\b(?:INR|Rs\\.?)\\s*", "")
				.replace(",", "").replace("₹", "").trim();
		boolean parenthesized = normalized.startsWith("(") && normalized.endsWith(")");
		if (parenthesized) {
			normalized = normalized.substring(1, normalized.length() - 1).trim();
		}
		try {
			BigDecimal amount = new BigDecimal(normalized);
			return parenthesized ? amount.negate() : amount;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private boolean amountIsNegative(String value) {
		return value != null && (value.trim().startsWith("-")
				|| (value.trim().startsWith("(") && value.trim().endsWith(")")));
	}

	private boolean isDebit(String value) {
		return value.contains("debit") || value.matches(".*\\bdr\\b.*");
	}

	private boolean isCredit(String value) {
		return value.contains("credit") || value.matches(".*\\bcr\\b.*");
	}

	private String detectBank(String fileName, List<List<String>> rows) {
		StringBuilder source = new StringBuilder(fileName == null ? "" : fileName).append(' ');
		for (int index = 0; index < Math.min(rows.size(), 30); index++) {
			source.append(String.join(" ", rows.get(index))).append(' ');
		}
		String normalized = source.toString().toLowerCase(Locale.ROOT);
		return BANKS.stream()
				.filter(bank -> bank.aliases().stream().anyMatch(alias -> containsAlias(normalized, alias)))
				.map(BankAlias::name)
				.findFirst()
				.orElse(null);
	}

	private boolean containsAlias(String source, String alias) {
		if (alias.length() <= 3) {
			return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])")
					.matcher(source).find();
		}
		return source.contains(alias);
	}

	private String fileExtension(String fileName) {
		if (fileName == null || !fileName.contains(".")) {
			return "";
		}
		return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	private String normalizeHeader(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private String cell(List<String> row, int index) {
		return index >= 0 && index < row.size() ? row.get(index).trim() : "";
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private record Header(
			int rowIndex,
			int dateColumn,
			int descriptionColumn,
			int debitColumn,
			int creditColumn,
			int amountColumn,
			int typeColumn) {
	}

	private record ParsedRow(Transaction transaction, boolean credit) {
		private static ParsedRow transaction(Transaction transaction) {
			return new ParsedRow(transaction, false);
		}

		private static ParsedRow creditRow() {
			return new ParsedRow(null, true);
		}

		private static ParsedRow skippedRow() {
			return new ParsedRow(null, false);
		}
	}

	private record BankAlias(String name, List<String> aliases) {
	}

	private record DatePrefix(LocalDate date, String remainder) {
	}

	public record Transaction(int rowNumber, LocalDate expenseDate, String description, BigDecimal amount,
			boolean assumedDebit) {
	}

	public record ParsedStatement(String detectedBankName, int skippedCredits, int skippedRows,
			List<Transaction> transactions) {
	}
}
