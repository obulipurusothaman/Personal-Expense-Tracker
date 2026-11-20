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
@Table(name = "device_link_codes")
public class DeviceLinkCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "code_hash", nullable = false, unique = true, length = 64)
	private String codeHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected DeviceLinkCode() {
	}

	public DeviceLinkCode(String codeHash, AppUser owner, Instant expiresAt) {
		this.codeHash = codeHash;
		this.owner = owner;
		this.expiresAt = expiresAt;
	}

	public AppUser getOwner() {
		return owner;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
