package com.example.demo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;

import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
			@org.springframework.security.core.annotation.AuthenticationPrincipal AppUser user,
			@CookieValue(name = BrowserProfileService.COOKIE_NAME, required = false) String token,
			HttpServletRequest request,
			HttpServletResponse response) {
		BrowserProfileService.BrowserProfile profile = user == null
				? browserProfileService.resolve(token)
				: browserProfileService.resolveForUser(user, token);
		ResponseCookie profileCookie = ResponseCookie.from(BrowserProfileService.COOKIE_NAME, profile.token())
				.httpOnly(true)
				.secure(request.isSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ofDays(365))
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, profileCookie.toString());
		return AuthUserDto.from(profile.user());
	}
}
