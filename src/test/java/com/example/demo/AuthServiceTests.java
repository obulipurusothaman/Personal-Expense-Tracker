package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:expense-auth-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthServiceTests {

	@Autowired
	private AuthService authService;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private BankAccountRepository accountRepository;

	@Autowired
	private ExpenseCategoryRepository categoryRepository;

	@Autowired
	private ExpenseRepository expenseRepository;

	@BeforeEach
	void clearData() {
		expenseRepository.deleteAll();
		accountRepository.deleteAll();
		categoryRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void firstUserClaimsLegacyAccountsAndCustomCategories() {
		BankAccount legacyAccount = accountRepository.save(
				new BankAccount("Legacy Bank", new BigDecimal("750.00"), null));
		ExpenseCategory builtInCategory = categoryRepository.save(new ExpenseCategory("Snacks", null));
		ExpenseCategory customCategory = categoryRepository.save(new ExpenseCategory("Legacy category", null));

		AppUser firstUser = authService.register(new RegisterRequest("Alice", "secure-password-123"));
		AppUser secondUser = authService.register(new RegisterRequest("Bob", "another-password-456"));
		ExpenseCategory savedCustomCategory = categoryRepository.findById(customCategory.getId()).orElseThrow();
		ExpenseCategory savedBuiltInCategory = categoryRepository.findById(builtInCategory.getId()).orElseThrow();

		assertTrue(accountRepository.findByIdAndOwner_Id(legacyAccount.getId(), firstUser.getId()).isPresent());
		assertTrue(accountRepository.findByOwner_Id(secondUser.getId()).isEmpty());
		assertEquals(firstUser.getId(), savedCustomCategory.getOwner().getId());
		assertNull(savedBuiltInCategory.getOwner());
		assertEquals(1, accountRepository.findByOwner_Id(firstUser.getId()).size());
	}
}
