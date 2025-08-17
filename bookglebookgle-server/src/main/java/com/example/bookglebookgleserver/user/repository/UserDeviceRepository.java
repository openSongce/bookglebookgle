package com.example.bookglebookgleserver.user.repository;

import com.example.bookglebookgleserver.user.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUser_IdAndToken(Long userId, String token);
    Optional<UserDevice> findByToken(String token);
    List<UserDevice> findAllByUser_IdAndEnabledTrue(Long userId);
    void deleteByUser_IdAndToken(Long userId, String token);
    List<UserDevice> findAllByToken(String token);

    // 여러 유저의 '활성' 디바이스 토큰만 조회
    @Query("""
        select d.token
          from UserDevice d
         where d.enabled = true
           and d.user.id in :userIds
           and d.token is not null
           and length(d.token) > 0
    """)
    List<String> findEnabledTokensByUserIds(@Param("userIds") Collection<Long> userIds);

}
