package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StatementFileParserTests {

	private final StatementFileParser parser = new StatementFileParser();

	@Test
	void parsesHdfcCsvDebitsAndSkipsCredits() {
		String csv = """
				HDFC Bank
				Transaction Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance
				20/11/2026,"UPI, COFFEE DAY",250.00,,750.00
				21/11/2026,Salary credit,,50000.00,50750.00
				""";

		StatementFileParser.ParsedStatement statement =
				parser.parse("hdfc-statement.csv", csv.getBytes(StandardCharsets.UTF_8));

		assertEquals("HDFC Bank", statement.detectedBankName());
		assertEquals(1, statement.skippedCredits());
		assertEquals(1, statement.transactions().size());
		assertEquals("UPI, COFFEE DAY", statement.transactions().get(0).description());
		assertEquals("250.00", statement.transactions().get(0).amount().toPlainString());
		assertEquals("2026-11-20", statement.transactions().get(0).expenseDate().toString());
	}

	@Test
	void parsesPipeDelimitedTextWithoutAHeader() {
		String text = "HDFC Bank\n20/11/2026|UPI COFFEE DAY|Rs. 250.00\n";

		StatementFileParser.ParsedStatement statement =
				parser.parse("statement.txt", text.getBytes(StandardCharsets.UTF_8));

		assertEquals(1, statement.transactions().size());
		assertEquals("250.00", statement.transactions().get(0).amount().toPlainString());
	}

	@Test
	void parsesDateGroupedTextLedgerAndAddsAmountsOnOneLine() {
		String text = """
				Current Balance October 30th - 21,208

				30th Oct 151 rs Item one
				Meals 100
				Snacks 35 + 70
				31Oct
				Train Ticket 10
				November 1st
				Item two 144
				November 6
				Credited 10k from another bank
				""";

		StatementFileParser.ParsedStatement statement =
				parser.parse("expenses.txt", text.getBytes(StandardCharsets.UTF_8));

		assertEquals(5, statement.transactions().size());
		assertEquals(1, statement.skippedCredits());
		assertEquals(LocalDate.now().getYear(), statement.transactions().get(0).expenseDate().getYear());
		assertEquals(LocalDate.of(LocalDate.now().getYear(), 10, 30),
				statement.transactions().get(0).expenseDate());
		assertEquals("151", statement.transactions().get(0).amount().toPlainString());
		assertEquals("Item one", statement.transactions().get(0).description());
		assertEquals("105", statement.transactions().get(2).amount().toPlainString());
		assertEquals(LocalDate.of(LocalDate.now().getYear(), 10, 31),
				statement.transactions().get(3).expenseDate());
		assertEquals(LocalDate.of(LocalDate.now().getYear(), 11, 1),
				statement.transactions().get(4).expenseDate());
	}

	@Test
	void parsesSelectableTextPdfStatements() throws Exception {
		byte[] pdf = createPdf(
				"Transaction Date,Narration,Debit Amount",
				"20/11/2026,UPI COFFEE DAY,250.00");

		StatementFileParser.ParsedStatement statement = parser.parse("hdfc-statement.pdf", pdf);

		assertEquals("HDFC Bank", statement.detectedBankName());
		assertEquals(1, statement.transactions().size());
		assertEquals("UPI COFFEE DAY", statement.transactions().get(0).description());
		assertEquals("250.00", statement.transactions().get(0).amount().toPlainString());
	}

	@Test
	void rejectsImageOnlyPdfWithAnActionableMessage() throws Exception {
		byte[] pdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(output);
			pdf = output.toByteArray();
		}

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class, () -> parser.parse("scanned-statement.pdf", pdf));

		assertTrue(exception.getReason().contains("no selectable text"));
	}

	@Test
	void parsesXlsxStatementRows() throws Exception {
		byte[] workbookBytes;
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("Statement");
			var header = sheet.createRow(0);
			header.createCell(0).setCellValue("Transaction Date");
			header.createCell(1).setCellValue("Narration");
			header.createCell(2).setCellValue("Withdrawal Amount");
			header.createCell(3).setCellValue("Deposit Amount");
			var transaction = sheet.createRow(1);
			transaction.createCell(0).setCellValue("20/11/2026");
			transaction.createCell(1).setCellValue("JIO Recharge");
			transaction.createCell(2).setCellValue(299.0);
			workbook.write(output);
			workbookBytes = output.toByteArray();
		}

		StatementFileParser.ParsedStatement statement = parser.parse("statement.xlsx", workbookBytes);

		assertEquals(1, statement.transactions().size());
		assertEquals("JIO Recharge", statement.transactions().get(0).description());
		assertEquals("299", statement.transactions().get(0).amount().toPlainString());
	}

	private byte[] createPdf(String... lines) throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
				content.setLeading(14);
				content.newLineAtOffset(25, 750);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}
}
