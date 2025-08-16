package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.entity.GroupMemberRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface GroupMemberRatingRepository extends JpaRepository<GroupMemberRating, Long> {
    boolean existsByGroup_IdAndFromMember_IdAndToMember_Id(Long groupId, Long fromId, Long toId);

    GroupMemberRating findByGroup_IdAndFromMember_IdAndToMember_Id(Long groupId, Long fromId, Long toId);

    List<GroupMemberRating> findByGroup_IdAndToMember_Id(Long groupId, Long toMemberId);

    List<GroupMemberRating> findByToMember_Id(Long toMemberId);

    @Query("""
           select r.toMember.id
             from GroupMemberRating r
            where r.group.id = :groupId
              and r.fromMember.id = :fromMemberId
           """)
    List<Long> findToMemberIdsByFromMemberIdAndGroupId(@Param("fromMemberId") Long fromMemberId,
                                                       @Param("groupId") Long groupId);

    // 🔹 추가: 그룹 전체의 (fromUserId, toUserId) 페어 벌크 조회
    @Query("""
           select r.fromMember.id, r.toMember.id
             from GroupMemberRating r
            where r.group.id = :groupId
           """)
    List<Object[]> findAllFromToPairsByGroupId(@Param("groupId") Long groupId);
}


