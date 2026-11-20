package com.example.demo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
	Optional<AppUser> findByUsernameIgnoreCase(String username);

	Optional<AppUser> findByProfileTokenHash(String profileTokenHash);

	Optional<AppUser> findFirstByProfileTokenHashIsNullOrderByIdAsc();

	boolean existsByUsernameIgnoreCase(String username);
}
