package com.example.bookglebookgleserver.comment.repository;

import com.example.bookglebookgleserver.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByGroupId(Long groupId);
    List<Comment> findByGroupIdAndUserId(Long groupId, Long userId);
}
