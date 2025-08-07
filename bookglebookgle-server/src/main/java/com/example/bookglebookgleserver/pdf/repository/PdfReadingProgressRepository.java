package com.example.bookglebookgleserver.pdf.repository;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.pdf.entity.PdfReadingProgress;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PdfReadingProgressRepository extends JpaRepository<PdfReadingProgress, Long> {
    Optional<PdfReadingProgress> findByUserAndGroup(User user, Group group);

    @Modifying
    @Transactional
    @Query("UPDATE PdfReadingProgress p SET p.lastReadPage = :page, p.updatedAt = CURRENT_TIMESTAMP WHERE p.user.id = :userId AND p.group.id = :groupId")
    void updateLastReadPage(@Param("userId") Long userId,
                            @Param("groupId") Long groupId,
                            @Param("page") int page);

}
