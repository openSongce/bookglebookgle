package com.example.bookglebookgleserver.group.service;


import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PdfProgressTxService {
    private final GroupMemberRepository groupMemberRepository;

    @Transactional /* 필요 시 propagation = REQUIRES_NEW */
    public int bump(Long userId, Long groupId, int page, int totalPages) {
        return groupMemberRepository.bumpProgress(userId, groupId, page, totalPages);
    }
}
