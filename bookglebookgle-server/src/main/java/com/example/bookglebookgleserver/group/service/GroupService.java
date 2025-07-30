package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.dto.GroupDetailResponse;
import com.example.bookglebookgleserver.group.dto.GroupListResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GroupService {
    void createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, String token);
    void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, String token);
    List<GroupListResponseDto> getGroupList();
    GroupDetailResponse getGroupDetail(Long groupId);
}
