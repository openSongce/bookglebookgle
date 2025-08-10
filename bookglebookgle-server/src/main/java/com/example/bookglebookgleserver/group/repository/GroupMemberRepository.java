package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.dto.GroupMemberDetailDto;
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


    @Query("""
select new com.example.bookglebookgleserver.group.dto.GroupMemberDetailDto(
    u.id,
    u.nickname,
    u.profileColor,
    coalesce(prp.maxReadPage, 0),
    case 
        when :pageCount > 0 then (coalesce(prp.maxReadPage, 0) * 100) / :pageCount
        else 0
    end,
    gm.isHost,
    case 
        when :pageCount > 0 and coalesce(prp.maxReadPage, 0) >= :pageCount then true 
        else false 
    end
)
from GroupMember gm
join gm.user u
left join PdfReadingProgress prp 
    on prp.group = gm.group and prp.user = u
where gm.group.id = :groupId
""")
    List<GroupMemberDetailDto> findMemberDetailsByGroupId(@Param("groupId") Long groupId,
                                                          @Param("pageCount") int pageCount);

    @Modifying
    @Query("""
    update GroupMember gm
       set gm.maxReadPage = case
           when :page > coalesce(gm.maxReadPage, 0) then :page
           else coalesce(gm.maxReadPage, 0)
         end,
           gm.progressPercent = case
           when :totalPages <= 0 then 0
           when (:page * 100) / :totalPages >= 100 then 100
           when (:page * 100) / :totalPages <= 0 then 0
           else (:page * 100) / :totalPages
         end
     where gm.user.id = :userId
       and gm.group.id = :groupId
""")
    int bumpProgress(@Param("userId") Long userId,
                     @Param("groupId") Long groupId,
                     @Param("page") int page,
                     @Param("totalPages") int totalPages);




    List<GroupMember> findByGroup(Group group);
}
