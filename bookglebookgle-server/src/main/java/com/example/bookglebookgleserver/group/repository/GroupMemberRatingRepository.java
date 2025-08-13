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

    // 이 그룹에서 '평가를 1건이라도 남긴(from_member)'의 group_member.id 집합
    @Query("""
      select distinct gmr.fromMember.id
      from GroupMemberRating gmr
      where gmr.group.id = :groupId
    """)
    Set<Long> findAllRaterMemberIdsByGroupId(@Param("groupId") Long groupId);


    //  특정 from_member가 이 그룹에서 평가를 남겼는지 단건 체크
    boolean existsByGroup_IdAndFromMember_Id(Long groupId, Long fromMemberId);


    @Query(value = """
    SELECT COUNT(DISTINCT to_member.user_id)
    FROM group_member_rating gmr
    JOIN group_member to_member ON gmr.to_member_id = to_member.id
    JOIN group_member from_member ON gmr.from_member_id = from_member.id
    WHERE gmr.group_id = :groupId
      AND from_member.user_id = :userId
""", nativeQuery = true)
    long countDistinctTargetsByUserInGroup(@Param("groupId") Long groupId,
                                           @Param("userId") Long userId);

}
