package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

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
}
