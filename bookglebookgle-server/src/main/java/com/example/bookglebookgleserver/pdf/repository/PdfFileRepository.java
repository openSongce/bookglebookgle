package com.example.bookglebookgleserver.pdf.repository;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfFileRepository extends JpaRepository<PdfFile, Long> {
}
