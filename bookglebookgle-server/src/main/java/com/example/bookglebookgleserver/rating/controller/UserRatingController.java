package com.example.bookglebookgleserver.rating.controller;

import com.example.bookglebookgleserver.rating.dto.RatingRequest;
import com.example.bookglebookgleserver.rating.service.UserRatingService;
import com.example.bookglebookgleserver.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserRatingController {


}