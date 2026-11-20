package com.example.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BrowserProfileAuthenticationFilter extends OncePerRequestFilter {

	private final BrowserProfileService browserProfileService;

	public BrowserProfileAuthenticationFilter(BrowserProfileService browserProfileService) {
		this.browserProfileService = browserProfileService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
				.filter(cookie -> BrowserProfileService.COOKIE_NAME.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);

		browserProfileService.findByToken(token).ifPresent(user -> {
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
					user, null, user.getAuthorities()));
			SecurityContextHolder.setContext(context);
		});

		filterChain.doFilter(request, response);
	}
}
