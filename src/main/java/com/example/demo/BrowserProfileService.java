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
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrowserProfileService {

	public static final String COOKIE_NAME = "PENNYWISE_PROFILE";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
	private static final Pattern DEVICE_LINK_CODE_PATTERN = Pattern.compile("[A-HJ-NP-Z2-9]{12}");
	private static final char[] DEVICE_LINK_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final AppUserRepository userRepository;
	private final BankAccountRepository accountRepository;
	private final ExpenseCategoryRepository categoryRepository;
	private final ExpenseRepository expenseRepository;
	private final BrowserProfileTokenRepository profileTokenRepository;
	private final DeviceLinkCodeRepository deviceLinkCodeRepository;
	private final PasswordEncoder passwordEncoder;

	public BrowserProfileService(
			AppUserRepository userRepository,
			BankAccountRepository accountRepository,
			ExpenseCategoryRepository categoryRepository,
			ExpenseRepository expenseRepository,
			BrowserProfileTokenRepository profileTokenRepository,
			DeviceLinkCodeRepository deviceLinkCodeRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.categoryRepository = categoryRepository;
		this.expenseRepository = expenseRepository;
		this.profileTokenRepository = profileTokenRepository;
		this.deviceLinkCodeRepository = deviceLinkCodeRepository;
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

	@Transactional(readOnly = true)
	public Optional<AppUser> findByToken(String token) {
		if (!isValidToken(token)) {
			return Optional.empty();
		}
		String tokenHash = hashToken(token);
		Optional<AppUser> profile = userRepository.findByProfileTokenHash(tokenHash);
		return profile.isPresent()
				? profile
				: profileTokenRepository.findByTokenHashAndExpiresAtAfter(tokenHash, Instant.now())
						.map(BrowserProfileToken::getOwner);
	}

	@Transactional
	public DeviceLinkCodeDto createDeviceLinkCode(AppUser owner) {
		String code = randomDeviceLinkCode();
		Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));
		deviceLinkCodeRepository.deleteByOwner_Id(owner.getId());
		deviceLinkCodeRepository.save(new DeviceLinkCode(
				hashToken(code), userRepository.getReferenceById(owner.getId()), expiresAt));
		return new DeviceLinkCodeDto(formatDeviceLinkCode(code), expiresAt);
	}

	@Transactional
	public LinkedProfile consumeDeviceLinkCode(String suppliedCode, AppUser currentUser) {
		String normalizedCode = normalizeDeviceLinkCode(suppliedCode);
		if (!DEVICE_LINK_CODE_PATTERN.matcher(normalizedCode).matches()) {
			throw invalidDeviceLinkCode();
		}

		DeviceLinkCode linkCode = deviceLinkCodeRepository
				.findByCodeHashForUpdate(hashToken(normalizedCode))
				.orElseThrow(BrowserProfileService::invalidDeviceLinkCode);
		if (!linkCode.getExpiresAt().isAfter(Instant.now())) {
			throw invalidDeviceLinkCode();
		}

		AppUser owner = linkCode.getOwner();
		if (currentUser != null
				&& !currentUser.getId().equals(owner.getId())
				&& hasProfileData(currentUser.getId())) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "This browser already has data; linking will not merge it.");
		}

		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
		profileTokenRepository.save(new BrowserProfileToken(
				hashToken(token),
				userRepository.getReferenceById(owner.getId()),
				Instant.now().plus(Duration.ofDays(365))));
		deviceLinkCodeRepository.delete(linkCode);
		return new LinkedProfile(owner, token);
	}

	private boolean hasProfileData(Long ownerId) {
		return accountRepository.existsByOwner_Id(ownerId)
				|| expenseRepository.existsByAccount_Owner_Id(ownerId)
				|| categoryRepository.existsByOwner_Id(ownerId);
	}

	private static ResponseStatusException invalidDeviceLinkCode() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link code is invalid, expired, or already used.");
	}

	private static String normalizeDeviceLinkCode(String code) {
		return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
	}

	private static String randomDeviceLinkCode() {
		char[] code = new char[12];
		for (int index = 0; index < code.length; index++) {
			code[index] = DEVICE_LINK_CODE_ALPHABET[SECURE_RANDOM.nextInt(DEVICE_LINK_CODE_ALPHABET.length)];
		}
		return new String(code);
	}

	private static String formatDeviceLinkCode(String code) {
		return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8);
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

	public record LinkedProfile(AppUser user, String token) {
	}
}
