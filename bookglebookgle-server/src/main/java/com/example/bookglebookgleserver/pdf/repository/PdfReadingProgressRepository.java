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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PdfReadingProgress p " +
            "set p.maxReadPage = function('greatest', p.maxReadPage, :page), " +
            "    p.updatedAt = CURRENT_TIMESTAMP " +
            "where p.user = :user and p.group = :group")
    int bumpMaxReadPage(@Param("user") User user,
                        @Param("group") Group group,
                        @Param("page") int page);
}
