package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.group.dto.*;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

public interface GroupService {

    GroupCreateResponseDto createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, User user);

    List<GroupListResponseDto> getNotJoinedGroupList(Long userId);

    GroupDetailResponse getGroupDetail(Long groupId, User user);

    GroupDetailResponse updateGroup(Long groupId, GroupUpdateRequestDto dto, User user);

    void deleteGroup(Long groupId, User user);

    List<GroupListResponseDto> searchGroups(String roomTitle, String category);

    void joinGroup(Long groupId, User user);

    void leaveGroup(Long groupId, User user);

    boolean isMember(Long groupId, Long userId);

    boolean isLeader(Long groupId, Long userId);

    int getLastPageRead(Long groupId, Long userId);

    List<MyGroupSummaryDto> getMyGroupList(Long userId);

    ResponseEntity<Resource> getPdfFileResponse(Long groupId, User user);

    ResponseEntity<StreamingResponseBody> getPdfResponse(Long groupId, User user, String accept);

    ResponseEntity<StreamingResponseBody> getPdfAndOcrZip(Long groupId, User user);

    void updateMemberMaxReadPage(Long groupId, Long userId, int newMaxReadPage);

    List<GroupMemberProgressDto> getGroupAllProgress(Long groupId, Long requesterId);

    List<CompletedBookDto> getCompletedBooks(Long userId);



}


