package com.example.bookglebookgleserver.fcm.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FcmQueryRepositoryImpl implements FcmQueryRepository {

    private final EntityManager em;

    @Override
    public List<String> findFcmTokensByGroupId(Long groupId) {
        List<String> tokens = em.createQuery("""
            select distinct d.token
            from UserDevice d
            where d.enabled = true
              and d.user.id in (
                select gm.user.id
                from GroupMember gm
                where gm.group.id = :gid
              )
        """, String.class)
                .setParameter("gid", groupId)
                .getResultList();

        log.debug("🔎 토큰 조회: groupId={}, count={}", groupId, tokens.size());
        return tokens;
    }
}
