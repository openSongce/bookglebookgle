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

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE pdf_reading_progress
           SET max_read_page = GREATEST(max_read_page, :page),
               updated_at = NOW()
         WHERE user_id = :userId
           AND group_id = :groupId
        """, nativeQuery = true)
    int bumpMaxReadPageById(@Param("userId") long userId,
                            @Param("groupId") long groupId,
                            @Param("page") int page);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE pdf_reading_progress
           SET progress_percent = :percent
         WHERE user_id = :userId
           AND group_id = :groupId
        """, nativeQuery = true)
    int updateProgressPercent(@Param("userId") long userId,
                              @Param("groupId") long groupId,
                              @Param("percent") int percent);

}
