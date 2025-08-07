package com.example.bookglebookgleserver.bookmark.repository;

import com.example.bookglebookgleserver.bookmark.entity.Bookmark;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByUserAndGroup(User user, Group group);

    List<Bookmark> findByUser(User user);

    Optional<Bookmark> findByUserAndGroupAndPage(User user, Group group, int page);
}
