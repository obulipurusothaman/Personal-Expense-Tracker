package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PeriodRangeTests {

	@Test
	void weekIncludesMondayThroughSundayOfSelectedDate() {
		PeriodRange range = PeriodRange.from("week", LocalDate.of(2026, 11, 19));

		assertEquals(LocalDate.of(2026, 11, 16), range.startDate());
		assertEquals(LocalDate.of(2026, 11, 22), range.endDate());
	}

	@Test
	void monthIncludesEntireSelectedMonth() {
		PeriodRange range = PeriodRange.from("month", LocalDate.of(2024, 2, 14));

		assertEquals(LocalDate.of(2024, 2, 1), range.startDate());
		assertEquals(LocalDate.of(2024, 2, 29), range.endDate());
	}
}
