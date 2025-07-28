package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/group")
public class GroupController {

    private final GroupService groupService;

    @PostMapping("/create")
    public ResponseEntity<?> createGroup(
            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,
            @RequestPart("file") MultipartFile pdfFile,
            @RequestHeader("Authorization") String token
    ) {
        groupService.createGroup(groupDto, pdfFile, token);
        return ResponseEntity.ok("스터디 그룹 생성 완료");
    }

    @PostMapping("/create/no-ocr")
    public ResponseEntity<?> createGroupWithoutOcr(
            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,
            @RequestPart("file") MultipartFile pdfFile,
            @RequestHeader("Authorization") String token
    ) {
        groupService.createGroupWithoutOcr(groupDto, pdfFile, token);
        return ResponseEntity.ok("OCR 없이 그룹 생성 완료");
    }
}

