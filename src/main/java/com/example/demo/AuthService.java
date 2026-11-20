package com.example.demo;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final AppUserRepository userRepository;
	private final BankAccountRepository accountRepository;
	private final ExpenseCategoryRepository categoryRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(
			AppUserRepository userRepository,
			BankAccountRepository accountRepository,
			ExpenseCategoryRepository categoryRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.categoryRepository = categoryRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AppUser register(RegisterRequest request) {
		String username = request.username().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByUsernameIgnoreCase(username)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already in use");
		}

		boolean firstUser = userRepository.count() == 0;
		AppUser user = userRepository.save(new AppUser(username, passwordEncoder.encode(request.password())));
		if (firstUser) {
			accountRepository.findByOwnerIsNull().forEach(account -> account.setOwner(user));
			categoryRepository.findByOwnerIsNull().stream()
					.filter(category -> !DefaultCategories.isBuiltIn(category.getName()))
					.forEach(category -> category.setOwner(user));
		}
		return user;
	}
}
