package com.example.bookglebookgleserver.user.repository;

import com.example.bookglebookgleserver.user.entity.PdfViewingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PdfViewingSessionRepository extends JpaRepository<PdfViewingSession, Long> {
    @Query("SELECT SUM(p.durationSeconds) FROM PdfViewingSession p WHERE p.user.id = :userId")
    Long sumTotalViewingTimeByUserId(@Param("userId") Long userId);


}
