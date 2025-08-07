package com.example.bookglebookgleserver.rating.repository;


import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.rating.entity.UserRating;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRatingRepository extends JpaRepository<UserRating, Long> {

    Optional<UserRating> findByRaterAndRateeAndGroup(User rater, User ratee, Group group);
}
