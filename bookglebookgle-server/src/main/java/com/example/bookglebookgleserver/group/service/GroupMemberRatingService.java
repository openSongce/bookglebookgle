package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMemberRating;
import com.example.bookglebookgleserver.group.repository.GroupMemberRatingRepository;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupMemberRatingService {

    private final GroupMemberRatingRepository groupMemberRatingRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;

    //평점계산
    private static final float BASELINE_SCORE = 3.0f;
    private static final int BASELINE_WEIGHT = 1;

    @Transactional
    public void addRating(Long groupId, Long fromId, Long toId, float score) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("본인은 본인을 평가할 수 없습니다.");
        }
        if (groupMemberRatingRepository.existsByGroup_IdAndFromMember_IdAndToMember_Id(groupId, fromId, toId)) {
            throw new IllegalArgumentException("이미 평가를 등록하였습니다. 수정만 가능합니다.");
        }
        Group group = groupRepository.findById(groupId).orElseThrow(() -> new IllegalArgumentException("그룹이 존재하지 않습니다."));
        User fromUser = userRepository.findById(fromId).orElseThrow(() -> new IllegalArgumentException("평가자 유저가 존재하지 않습니다."));
        User toUser = userRepository.findById(toId).orElseThrow(() -> new IllegalArgumentException("평가 대상 유저가 존재하지 않습니다."));

        GroupMemberRating rating = GroupMemberRating.builder()
                .group(group)
                .fromMember(fromUser)
                .toMember(toUser)
                .score(score)
                .build();
        groupMemberRatingRepository.save(rating);

        // 평점 등록 후 평균 점수 반영
        updateUserAverageRating(toId);
    }

    @Transactional
    public void updateRating(Long groupId, Long fromId, Long toId, float score) {
        GroupMemberRating rating = groupMemberRatingRepository.findByGroup_IdAndFromMember_IdAndToMember_Id(groupId, fromId, toId);
        if (rating == null) throw new IllegalArgumentException("평가 내역이 없습니다. 먼저 등록해 주세요.");
        rating.setScore(score);
        groupMemberRatingRepository.save(rating);

        // 평점 수정 후 평균 점수 반영
        updateUserAverageRating(toId);
    }

    // 평균 평점 반환
    public Float getAverageRating(Long groupId, Long toId) {
        List<GroupMemberRating> ratings =
                groupMemberRatingRepository.findByGroup_IdAndToMember_Id(groupId, toId);

        int n = ratings.size();
        double sum = ratings.stream().mapToDouble(GroupMemberRating::getScore).sum();

        // 가상의 3점 1표를 포함
        double smoothed = (sum + BASELINE_SCORE * BASELINE_WEIGHT) / (n + BASELINE_WEIGHT);

        // 필요하면 반올림 자리수 조정
        return (float) smoothed;
    }

    // users 테이블 avg_rating 반영 메서드
    private void updateUserAverageRating(Long toUserId) {
        List<GroupMemberRating> allRatings =
                groupMemberRatingRepository.findByToMember_Id(toUserId);

        int n = allRatings.size();
        double sum = allRatings.stream().mapToDouble(GroupMemberRating::getScore).sum();

        // 가상의 3점 1표를 포함
        float avg = (float) ((sum + BASELINE_SCORE * BASELINE_WEIGHT) / (n + BASELINE_WEIGHT));

        User user = userRepository.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("평가 대상 유저가 존재하지 않습니다."));
        user.setAvgRating(avg);
        userRepository.save(user);
    }
}
