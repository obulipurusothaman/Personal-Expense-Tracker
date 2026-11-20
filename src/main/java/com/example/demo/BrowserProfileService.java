package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrowserProfileService {

	public static final String COOKIE_NAME = "PENNYWISE_PROFILE";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final AppUserRepository userRepository;
	private final BankAccountRepository accountRepository;
	private final ExpenseCategoryRepository categoryRepository;
	private final PasswordEncoder passwordEncoder;

	public BrowserProfileService(
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
	public BrowserProfile resolve(String token) {
		Optional<AppUser> existingProfile = findByToken(token);
		if (existingProfile.isPresent()) {
			return new BrowserProfile(existingProfile.get(), token);
		}

		long existingUsers = userRepository.count();
		AppUser user = existingUsers == 1
				? userRepository.findFirstByProfileTokenHashIsNullOrderByIdAsc().orElse(null)
				: null;
		boolean claimLegacyData = existingUsers == 0 || user != null;
		if (user == null) {
			String username = "browser-" + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
			String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
			user = userRepository.save(new AppUser(username, passwordEncoder.encode(temporaryPassword)));
		}

		return issueProfile(user, claimLegacyData);
	}

	@Transactional
	public BrowserProfile resolveForUser(AppUser user, String token) {
		if (isValidToken(token) && hashToken(token).equals(user.getProfileTokenHash())) {
			return new BrowserProfile(user, token);
		}
		return issueProfile(user, userRepository.count() == 1);
	}

	public Optional<AppUser> findByToken(String token) {
		if (!isValidToken(token)) {
			return Optional.empty();
		}
		return userRepository.findByProfileTokenHash(hashToken(token));
	}

	private BrowserProfile issueProfile(AppUser user, boolean claimLegacyData) {
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
		user.setProfileTokenHash(hashToken(token));
		userRepository.save(user);
		if (claimLegacyData) {
			accountRepository.findByOwnerIsNull().forEach(account -> account.setOwner(user));
			categoryRepository.findByOwnerIsNull().stream()
					.filter(category -> !DefaultCategories.isBuiltIn(category.getName()))
					.forEach(category -> category.setOwner(user));
		}
		return new BrowserProfile(user, token);
	}

	private static boolean isValidToken(String token) {
		return token != null && TOKEN_PATTERN.matcher(token).matches();
	}

	static String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.US_ASCII));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required to secure browser profiles", exception);
		}
	}

	private static byte[] randomBytes() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return bytes;
	}

	public record BrowserProfile(AppUser user, String token) {
	}
}
