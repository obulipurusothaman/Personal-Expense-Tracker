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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "bank_accounts", uniqueConstraints = @UniqueConstraint(
		name = "uk_bank_accounts_owner_client_request", columnNames = { "owner_id", "client_request_id" }))
public class BankAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false)
	private String bankName;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal balance;

	@Column(name = "client_request_id", length = 36)
	private String clientRequestId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private AppUser owner;

	protected BankAccount() {
	}

	public BankAccount(String bankName, BigDecimal balance, AppUser owner) {
		this(bankName, balance, owner, null);
	}

	public BankAccount(String bankName, BigDecimal balance, AppUser owner, String clientRequestId) {
		this.bankName = bankName;
		this.balance = balance;
		this.owner = owner;
		this.clientRequestId = clientRequestId;
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

	public String getClientRequestId() {
		return clientRequestId;
	}

	public void setOwner(AppUser owner) {
		this.owner = owner;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}
}
