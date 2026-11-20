package com.example.demo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final BrowserProfileService browserProfileService;

	public AuthController(BrowserProfileService browserProfileService) {
		this.browserProfileService = browserProfileService;
	}

	@GetMapping("/csrf")
	public CsrfToken csrfToken(CsrfToken csrfToken) {
		return csrfToken;
	}

	@GetMapping("/me")
	public AuthUserDto currentUser(
			@AuthenticationPrincipal AppUser user,
			@CookieValue(name = BrowserProfileService.COOKIE_NAME, required = false) String token,
			HttpServletRequest request,
			HttpServletResponse response) {
		BrowserProfileService.BrowserProfile profile = user == null
				? browserProfileService.resolve(token)
				: browserProfileService.resolveForUser(user, token);
		setProfileCookie(profile.token(), request, response);
		return AuthUserDto.from(profile.user());
	}

	@PostMapping("/device-link-codes")
	public DeviceLinkCodeDto createDeviceLinkCode(@AuthenticationPrincipal AppUser user) {
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Open the app before linking a device.");
		}
		return browserProfileService.createDeviceLinkCode(user);
	}

	@PostMapping("/device-links")
	public AuthUserDto linkDevice(
			@Valid @RequestBody DeviceLinkRequest request,
			@AuthenticationPrincipal AppUser currentUser,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		BrowserProfileService.LinkedProfile linkedProfile =
				browserProfileService.consumeDeviceLinkCode(request.code(), currentUser);
		setProfileCookie(linkedProfile.token(), httpRequest, httpResponse);
		return AuthUserDto.from(linkedProfile.user());
	}

	private void setProfileCookie(
			String token, HttpServletRequest request, HttpServletResponse response) {
		ResponseCookie profileCookie = ResponseCookie.from(BrowserProfileService.COOKIE_NAME, token)
				.httpOnly(true)
				.secure(request.isSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofDays(365))
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, profileCookie.toString());
	}
}
