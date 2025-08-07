package com.example.bookglebookgleserver.user.repository;

import com.example.bookglebookgleserver.user.entity.PdfViewingSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfViewingSessionRepository extends JpaRepository<PdfViewingSession, Long> {
}
