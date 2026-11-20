package com.example.demo;

import java.math.BigDecimal;

public record AccountDto(Long id, String bankName, BigDecimal balance) {
	public static AccountDto from(BankAccount account) {
		return new AccountDto(account.getId(), account.getBankName(), account.getBalance());
	}
}
