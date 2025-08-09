package com.example.bookglebookgleserver.user.repository;

import com.example.bookglebookgleserver.user.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUser_IdAndToken(Long userId, String token);
    Optional<UserDevice> findByToken(String token);
    List<UserDevice> findAllByUser_IdAndEnabledTrue(Long userId);
    void deleteByUser_IdAndToken(Long userId, String token);
}
