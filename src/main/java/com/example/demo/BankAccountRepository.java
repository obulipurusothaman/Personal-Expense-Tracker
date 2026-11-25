package com.example.demo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
	List<BankAccount> findByOwner_Id(Long ownerId);

	boolean existsByOwner_Id(Long ownerId);

	Optional<BankAccount> findByOwner_IdAndClientRequestId(Long ownerId, String clientRequestId);

	Optional<BankAccount> findByIdAndOwner_Id(Long id, Long ownerId);

	List<BankAccount> findByOwnerIsNull();
}
