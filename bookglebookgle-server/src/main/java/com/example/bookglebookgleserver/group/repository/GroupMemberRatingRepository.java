package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.entity.GroupMemberRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRatingRepository extends JpaRepository<GroupMemberRating, Long> {
    boolean existsByGroup_IdAndFromMember_IdAndToMember_Id(Long groupId, Long fromId, Long toId);

    GroupMemberRating findByGroup_IdAndFromMember_IdAndToMember_Id(Long groupId, Long fromId, Long toId);

    List<GroupMemberRating> findByGroup_IdAndToMember_Id(Long groupId, Long toMemberId);

    List<GroupMemberRating> findByToMember_Id(Long toMemberId);
}
