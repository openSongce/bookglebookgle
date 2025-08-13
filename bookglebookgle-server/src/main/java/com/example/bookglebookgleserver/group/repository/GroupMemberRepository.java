package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.dto.GroupMemberDetailDto;
import com.example.bookglebookgleserver.group.dto.GroupMemberProgressDto;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    int countByGroup(Group group);
    @Query("select count(gm) > 0 from GroupMember gm " +
            "where gm.group.id = :groupId and gm.user.id = :userId")
    boolean isMember(@Param("groupId") Long groupId, @Param("userId") Long userId);
    List<GroupMember> findByUser_Id(Long userId);
    boolean existsByGroupAndUser(Group group, User user);
    Optional<GroupMember> findByGroupAndUser(Group group, User user);
    @Query("SELECT gm.group.id FROM GroupMember gm WHERE gm.user.id = :userId")
    List<Long> findGroupIdsByUserId(@Param("userId") Long userId);

    // 특정 사용자가 참가한 모임 수 조회
    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.user.id = :userId")
    int countJoinedGroupsByUserId(@Param("userId") Long userId);

    // 완료한 모임 수
    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.user.id = :userId AND gm.progressPercent >= 100")
    int countCompletedGroupsByUserId(@Param("userId") Long userId);

    // 미완료한 모임 수
    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.user.id = :userId AND gm.progressPercent < 100")
    int countIncompleteGroupsByUserId(@Param("userId") Long userId);

    List<GroupMember> findByGroup_Id(Long groupId);


    @Query("""
select new com.example.bookglebookgleserver.group.dto.GroupMemberDetailDto(
  u.id,
  u.nickname,
  u.profileColor,
  coalesce(gm.maxReadPage, 0),
  cast(round(coalesce(gm.progressPercent, 0)) as integer),
  gm.isHost,
  false
)
from GroupMember gm join gm.user u
where gm.group.id = :groupId
""")
    List<GroupMemberDetailDto> findMemberDetailsByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("""
update GroupMember gm
   set gm.maxReadPage = case
       when :page > coalesce(gm.maxReadPage, 0) then :page
       else coalesce(gm.maxReadPage, 0)
     end,
       gm.progressPercent = case
       when :totalPages <= 0 then 0
       else least(100,
            greatest(0,
                cast( round( ((least(:page, :totalPages - 1) + 1) * 100.0) / :totalPages ) as integer )
            )
       )
     end
 where gm.user.id = :userId
   and gm.group.id = :groupId
""")
    int bumpProgress(@Param("userId") Long userId,
                     @Param("groupId") Long groupId,
                     @Param("page") int page,
                     @Param("totalPages") int totalPages);


    @Query("""
select new com.example.bookglebookgleserver.group.dto.GroupMemberProgressDto(
  u.id,
  u.nickname,
  coalesce(gm.maxReadPage, 0),
  cast(round(coalesce(gm.progressPercent, 0)) as integer)
)
from GroupMember gm
  join gm.user u
where gm.group.id = :groupId
order by u.id
""")
    List<GroupMemberProgressDto> findAllMemberProgressByGroupId(@Param("groupId") Long groupId);


    @Query("SELECT gm.id FROM GroupMember gm WHERE gm.user.id = :userId AND gm.group.id = :groupId")
    Optional<Long> findGroupMemberIdByUserIdAndGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);



    List<GroupMember> findByGroup(Group group);
}
