package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import org.springframework.web.multipart.MultipartFile;

public interface GroupService {
    void createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, String token);
    void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, String token);
}
