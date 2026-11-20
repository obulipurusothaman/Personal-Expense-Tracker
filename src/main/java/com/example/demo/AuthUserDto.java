package com.example.demo;

public record AuthUserDto(Long id, String username) {
	public static AuthUserDto from(AppUser user) {
		return new AuthUserDto(user.getId(), user.getUsername());
	}
}
