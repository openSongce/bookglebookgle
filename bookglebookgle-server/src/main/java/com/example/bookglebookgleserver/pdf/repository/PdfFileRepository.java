package com.example.bookglebookgleserver.pdf.repository;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PdfFileRepository extends JpaRepository<PdfFile, Long> {
    Optional<PdfFile> findByGroup_Id(Long groupId);
}
