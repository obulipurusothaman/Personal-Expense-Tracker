package com.example.demo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record PeriodRange(LocalDate startDate, LocalDate endDate) {

	public static PeriodRange from(String period, LocalDate date) {
		LocalDate selectedDate = date == null ? LocalDate.now() : date;
		String selectedPeriod = period == null ? "month" : period.toLowerCase();
		return switch (selectedPeriod) {
			case "day" -> new PeriodRange(selectedDate, selectedDate);
			case "week" -> new PeriodRange(
					selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
					selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
			case "month" -> new PeriodRange(
					selectedDate.withDayOfMonth(1),
					selectedDate.with(TemporalAdjusters.lastDayOfMonth()));
			case "year" -> new PeriodRange(
					selectedDate.withDayOfYear(1),
					selectedDate.with(TemporalAdjusters.lastDayOfYear()));
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Period must be day, week, month, or year");
		};
	}
}
