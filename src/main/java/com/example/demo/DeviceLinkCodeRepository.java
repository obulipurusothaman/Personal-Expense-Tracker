package com.example.demo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DeviceLinkCodeRepository extends JpaRepository<DeviceLinkCode, Long> {
	void deleteByOwner_Id(Long ownerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select linkCode from DeviceLinkCode linkCode where linkCode.codeHash = :codeHash")
	Optional<DeviceLinkCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
