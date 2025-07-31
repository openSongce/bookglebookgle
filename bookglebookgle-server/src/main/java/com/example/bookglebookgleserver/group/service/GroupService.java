package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.dto.GroupDetailResponse;
import com.example.bookglebookgleserver.group.dto.GroupListResponseDto;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GroupService {
    void createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, User user);
    void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, User user);
    List<GroupListResponseDto> getGroupList();
    GroupDetailResponse getGroupDetail(Long groupId);
    ResponseEntity<Resource> getPdfFileResponse(Long groupId, User user);
}
