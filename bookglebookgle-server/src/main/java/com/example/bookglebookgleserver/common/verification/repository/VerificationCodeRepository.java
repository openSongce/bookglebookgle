package com.example.bookglebookgleserver.common.verification.repository;


import com.example.bookglebookgleserver.common.verification.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode,Long> {
    Optional<VerificationCode> findTopByEmailOrderByCreatedAtDesc(String email);
}
