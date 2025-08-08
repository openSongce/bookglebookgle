package com.example.bookglebookgleserver.fcm.repository;

import java.util.List;

public interface FcmQueryRepository {
    List<String> findFcmTokensByGroupId(Long groupId);
}
