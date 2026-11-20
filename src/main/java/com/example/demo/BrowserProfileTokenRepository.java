package com.example.demo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrowserProfileTokenRepository extends JpaRepository<BrowserProfileToken, Long> {
	Optional<BrowserProfileToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant expiresAt);
}
