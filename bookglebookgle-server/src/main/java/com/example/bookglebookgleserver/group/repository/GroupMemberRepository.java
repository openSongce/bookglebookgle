package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    int countByGroup(Group group);
    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);
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
    int countCompletedGroupsByProgress(@Param("userId") Long userId);

    // 미완료한 모임 수
    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.user.id = :userId AND gm.progressPercent < 100")
    int countIncompleteGroupsByProgress(@Param("userId") Long userId);

}
