package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.group.dto.*;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GroupService {
    GroupCreateResponseDto createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, User user);
    void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, User user);
    List<GroupListResponseDto> getGroupList();
    GroupDetailResponse getGroupDetail(Long groupId);
    ResponseEntity<Resource> getPdfFileResponse(Long groupId, User user);
    List<MyGroupSummaryDto> getMyGroupList(Long userId);
    void joinGroup(Long groupId, User user);
}
