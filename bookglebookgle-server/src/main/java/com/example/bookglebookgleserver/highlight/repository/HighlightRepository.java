package com.example.bookglebookgleserver.highlight.repository;

import com.example.bookglebookgleserver.highlight.entity.Highlight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HighlightRepository extends JpaRepository<Highlight, Long> {
    List<Highlight> findByGroup_Id(Long groupId);
}
