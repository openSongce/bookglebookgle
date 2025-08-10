package com.example.bookglebookgleserver.lastPage;

import com.example.bookglebookgleserver.group.dto.GroupMemberDetailDto; // 또는 GroupMemberLite
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.entity.PdfReadingProgress;
import com.example.bookglebookgleserver.pdf.repository.PdfReadingProgressRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.test.context.ActiveProfiles("test")
class GroupMemberRepositoryTest {

    @Autowired
    GroupRepository groupRepository;
    @Autowired
    GroupMemberRepository groupMemberRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PdfReadingProgressRepository pdfReadingProgressRepository;

    @Test
    void 프로젝션으로_maxReadPage_값이_들어온다() {
        // given
        User u = userRepository.save(User.builder()
                .email("u@test.com")
                .nickname("nick")
                .password("pw") // User 제약조건에 맞게 필요 시 추가
                .build());

        // 1) PdfFile 먼저 준비 (PdfFile 필수 필드 채우기)
        PdfFile pdf = PdfFile.builder()
                .fileName("test.pdf")
                .filePath("/tmp/test.pdf")    // NOT NULL
                .pageCnt(100)                 // NOT NULL
                .uploadUser(u)                // NOT NULL (user FK)
                .createdAt(LocalDateTime.now()) // NOT NULL
                .imageBased(false)            // NOT NULL
                .hasOcr(false)                // NOT NULL
                .build();

        // 2) Group 생성 + pdf 연결
        Group g = Group.builder()
                .roomTitle("room")
                .category(Group.Category.READING)
                .hostUser(u)
                .groupMaxNum(10)
                .minRequiredRating(0)
                .totalPages(100)
                .build();
        g.setPdfFile(pdf); // ★ 필수 (nullable=false)

        groupRepository.save(g); // cascade=ALL이라 pdf도 함께 저장됨

        // 3) GroupMember / PdfReadingProgress 저장
        groupMemberRepository.save(GroupMember.builder()
                .group(g).user(u)
                .isHost(true)
                .maxReadPage(0)
                .progressPercent(0f)
                .isFollowingHost(false)
                .build());

        pdfReadingProgressRepository.save(PdfReadingProgress.builder()
                .user(u).group(g)
                .maxReadPage(30)
                .build());

        // when
        // 한 방 프로젝션(Detail) 쓰는 경우
        List<GroupMemberDetailDto> rows = groupMemberRepository.findMemberDetailsByGroupId(g.getId(), 100);

        // then
        assertThat(rows).hasSize(1);
        var dto = rows.get(0);

        // DTO 필드명이 lastPageRead 라면:
        assertThat(dto.maxReadPage()).isEqualTo(30);

        // 📌 DB 상태 로그 출력 (GroupMember & PdfReadingProgress)
        System.out.println("=== [DB] group_member 테이블 ===");
        groupMemberRepository.findAll().forEach(m -> {
            System.out.printf("userId=%d, groupId=%d, maxReadPage=%d, progressPercent=%.2f%n",
                    m.getUser().getId(), m.getGroup().getId(),
                    m.getMaxReadPage(), m.getProgressPercent());
        });

        System.out.println("=== [DB] pdf_reading_progress 테이블 ===");
        pdfReadingProgressRepository.findAll().forEach(p -> {
            System.out.printf("userId=%d, groupId=%d, maxReadPage=%d%n",
                    p.getUser().getId(), p.getGroup().getId(), p.getMaxReadPage());
        });

        // (DTO를 maxReadPage로 바꿨다면 위 줄 대신)
        // assertThat(dto.maxReadPage()).isEqualTo(30);
    }

}

