package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    int countByGroup(Group group);
    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);
    List<GroupMember> findByUser_Id(Long userId);
    boolean existsByGroupAndUser(Group group, User user);
}
