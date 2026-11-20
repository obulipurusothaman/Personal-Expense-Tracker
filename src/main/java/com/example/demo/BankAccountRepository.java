package com.example.demo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
	List<BankAccount> findByOwner_Id(Long ownerId);

	Optional<BankAccount> findByIdAndOwner_Id(Long id, Long ownerId);

	List<BankAccount> findByOwnerIsNull();
}
