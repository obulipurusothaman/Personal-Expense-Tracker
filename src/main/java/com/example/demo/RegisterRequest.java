package com.example.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,40}$") String username,
		@NotBlank @Size(min = 12, max = 72) String password) {
}
