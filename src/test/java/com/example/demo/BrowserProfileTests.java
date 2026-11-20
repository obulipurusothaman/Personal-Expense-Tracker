package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:expense-browser-profile-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class BrowserProfileTests {

	@Autowired
	private BrowserProfileService browserProfileService;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private BankAccountRepository accountRepository;

	@Autowired
	private ExpenseCategoryRepository categoryRepository;

	@Autowired
	private ExpenseRepository expenseRepository;

	@Autowired
	private BrowserProfileTokenRepository browserProfileTokenRepository;

	@Autowired
	private DeviceLinkCodeRepository deviceLinkCodeRepository;

	@BeforeEach
	void clearData() {
		expenseRepository.deleteAll();
		accountRepository.deleteAll();
		categoryRepository.deleteAll();
		deviceLinkCodeRepository.deleteAll();
		browserProfileTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createsSeparateProfilesAndRestoresAProfileFromItsToken() {
		BankAccount legacyAccount = accountRepository.save(
				new BankAccount("Legacy Bank", new BigDecimal("750.00"), null));
		ExpenseCategory customCategory = categoryRepository.save(new ExpenseCategory("Legacy category", null));

		BrowserProfileService.BrowserProfile first = browserProfileService.resolve(null);
		BrowserProfileService.BrowserProfile restored = browserProfileService.resolve(first.token());
		BrowserProfileService.BrowserProfile second = browserProfileService.resolve(null);

		assertEquals(first.user().getId(), restored.user().getId());
		assertNotEquals(first.user().getId(), second.user().getId());
		assertTrue(accountRepository.findByIdAndOwner_Id(legacyAccount.getId(), first.user().getId()).isPresent());
		assertTrue(accountRepository.findByOwner_Id(second.user().getId()).isEmpty());
		assertEquals(first.user().getId(),
				categoryRepository.findById(customCategory.getId()).orElseThrow().getOwner().getId());
	}

	@Test
	void singleExistingUserIsAdoptedForTheFirstBrowserProfile() {
		AppUser existingUser = userRepository.save(new AppUser("existing-user", "legacy-password-hash"));

		BrowserProfileService.BrowserProfile profile = browserProfileService.resolve(null);

		assertEquals(existingUser.getId(), profile.user().getId());
		assertNotNull(profile.user().getProfileTokenHash());
	}

	@Test
	void profileCookieAuthenticatesBrowserScopedApiRequests() throws Exception {
		BrowserProfileService.BrowserProfile profile = browserProfileService.resolve(null);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie(BrowserProfileService.COOKIE_NAME, profile.token()));
		MockHttpServletResponse response = new MockHttpServletResponse();

		new BrowserProfileAuthenticationFilter(browserProfileService)
				.doFilter(request, response, new MockFilterChain());

		assertNotNull(SecurityContextHolder.getContext().getAuthentication());
		assertEquals(profile.user().getId(),
				((AppUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
	}

	@Test
	void profileEndpointIssuesLongLivedHttpOnlyCookie() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSecure(true);
		MockHttpServletResponse response = new MockHttpServletResponse();

		new AuthController(browserProfileService).currentUser(null, null, request, response);

		String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertNotNull(cookie);
		assertTrue(cookie.contains("Secure"));
		assertTrue(cookie.contains("HttpOnly"));
		assertTrue(cookie.contains("SameSite=Lax"));
		assertTrue(cookie.contains("Max-Age=31536000"));
	}

	@Test
	void oneTimeCodeLinksASecondDeviceToTheSameProfile() {
		AppUser owner = browserProfileService.resolve(null).user();
		DeviceLinkCodeDto linkCode = browserProfileService.createDeviceLinkCode(owner);

		BrowserProfileService.LinkedProfile linkedProfile =
				browserProfileService.consumeDeviceLinkCode(linkCode.code(), null);

		assertEquals(owner.getId(), linkedProfile.user().getId());
		assertEquals(owner.getId(), browserProfileService.findByToken(linkedProfile.token()).orElseThrow().getId());
		assertThrows(ResponseStatusException.class,
				() -> browserProfileService.consumeDeviceLinkCode(linkCode.code(), null));
	}

	@Test
	void linkingDoesNotOverwriteADeviceProfileWithExistingData() {
		AppUser owner = browserProfileService.resolve(null).user();
		AppUser existingPhoneProfile = browserProfileService.resolve(null).user();
		accountRepository.save(new BankAccount("Phone bank", new BigDecimal("100.00"), existingPhoneProfile));
		DeviceLinkCodeDto linkCode = browserProfileService.createDeviceLinkCode(owner);

		assertThrows(ResponseStatusException.class,
				() -> browserProfileService.consumeDeviceLinkCode(linkCode.code(), existingPhoneProfile));
		assertEquals(1, deviceLinkCodeRepository.count());
		assertEquals(1, accountRepository.findByOwner_Id(existingPhoneProfile.getId()).size());
	}
}
