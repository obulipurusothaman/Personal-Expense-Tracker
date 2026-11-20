package com.example.demo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "browser_profile_tokens")
public class BrowserProfileToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected BrowserProfileToken() {
	}

	public BrowserProfileToken(String tokenHash, AppUser owner, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.owner = owner;
		this.expiresAt = expiresAt;
	}

	public AppUser getOwner() {
		return owner;
	}
}
