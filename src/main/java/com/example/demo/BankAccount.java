package com.example.demo;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal balance;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private AppUser owner;

	protected BankAccount() {
	}

	public BankAccount(String bankName, BigDecimal balance, AppUser owner) {
		this.bankName = bankName;
		this.balance = balance;
		this.owner = owner;
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

	public AppUser getOwner() {
		return owner;
	}

	public void setOwner(AppUser owner) {
		this.owner = owner;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}
}
