package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "expenses", uniqueConstraints = @UniqueConstraint(
		name = "uk_expenses_account_client_request", columnNames = { "account_id", "client_request_id" }))
public class Expense {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private BankAccount account;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private ExpenseCategory category;

	@Column(nullable = false, length = 120)
	private String description;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private LocalDate expenseDate;

	@Column(name = "client_request_id", length = 36)
	private String clientRequestId;

	@Column(name = "balance_adjusted", nullable = false, columnDefinition = "boolean default true")
	private boolean balanceAdjusted = true;

	protected Expense() {
	}

	public Expense(BankAccount account, ExpenseCategory category, String description, BigDecimal amount,
			LocalDate expenseDate) {
		this(account, category, description, amount, expenseDate, null);
	}

	public Expense(BankAccount account, ExpenseCategory category, String description, BigDecimal amount,
			LocalDate expenseDate, String clientRequestId) {
		this(account, category, description, amount, expenseDate, clientRequestId, true);
	}

	public Expense(BankAccount account, ExpenseCategory category, String description, BigDecimal amount,
			LocalDate expenseDate, String clientRequestId, boolean balanceAdjusted) {
		this.account = account;
		this.category = category;
		this.description = description;
		this.amount = amount;
		this.expenseDate = expenseDate;
		this.clientRequestId = clientRequestId;
		this.balanceAdjusted = balanceAdjusted;
	}

	public Long getId() {
		return id;
	}

	public BankAccount getAccount() {
		return account;
	}

	public ExpenseCategory getCategory() {
		return category;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getExpenseDate() {
		return expenseDate;
	}

	public boolean isBalanceAdjusted() {
		return balanceAdjusted;
	}

	public void setBalanceAdjusted(boolean balanceAdjusted) {
		this.balanceAdjusted = balanceAdjusted;
	}
}
