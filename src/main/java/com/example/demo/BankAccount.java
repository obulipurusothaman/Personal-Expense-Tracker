package com.example.demo;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false)
	private String bankName;

	@DecimalMin("0.00")
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal balance;

	protected BankAccount() {
	}

	public BankAccount(String bankName, BigDecimal balance) {
		this.bankName = bankName;
		this.balance = balance;
	}

	public Long getId() {
		return id;
	}

	public String getBankName() {
		return bankName;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}
}
