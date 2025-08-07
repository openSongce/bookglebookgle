package com.example.bookglebookgleserver.pdf.repository;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.pdf.entity.PdfReadingProgress;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PdfReadingProgressRepository extends JpaRepository<PdfReadingProgress, Long> {
    Optional<PdfReadingProgress> findByUserAndGroup(User user, Group group);
}
