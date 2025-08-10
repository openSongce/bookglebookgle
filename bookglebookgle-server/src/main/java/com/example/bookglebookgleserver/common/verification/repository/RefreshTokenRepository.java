package com.example.bookglebookgleserver.common.verification.repository;

import com.example.bookglebookgleserver.common.verification.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,String> {
    Optional<RefreshToken> findById(String email);

    void deleteByEmail(String email);

}
