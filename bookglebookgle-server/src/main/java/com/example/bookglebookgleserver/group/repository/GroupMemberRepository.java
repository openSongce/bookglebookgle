package com.example.bookglebookgleserver.group.repository;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    int countByGroup(Group group);
    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);
}
