package com.example.bookglebookgleserver.rating.service;


import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.rating.dto.RatingRequest;
import com.example.bookglebookgleserver.rating.entity.UserRating;
import com.example.bookglebookgleserver.rating.repository.UserRatingRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRatingService {
    private final UserRatingRepository userRatingRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public void submitRating(Long raterId, Long rateeId, RatingRequest request) {

    }

}
