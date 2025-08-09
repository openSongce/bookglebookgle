package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("SELECT g FROM Group g " +
            "WHERE (:roomTitle IS NULL OR g.roomTitle LIKE %:roomTitle%) " +
            "AND (:category IS NULL OR g.category = :category)")
    List<Group> searchGroups(
            @Param("roomTitle") String roomTitle,
            @Param("category") com.example.bookglebookgleserver.group.entity.Group.Category category
    );

    @Query("""
    select g from Group g
    join fetch g.pdfFile pf
    left join fetch g.members gm
    left join fetch gm.user u
    where g.id = :groupId
    """)
    Optional<Group> findByIdWithPdfAndMembers(@Param("groupId") Long groupId);

}
