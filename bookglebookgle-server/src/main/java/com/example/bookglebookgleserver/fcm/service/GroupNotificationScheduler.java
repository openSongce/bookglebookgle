package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.fcm.util.KoreanScheduleParser;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupNotificationScheduler {

    private final ThreadPoolTaskScheduler scheduler;
    private final GroupRepository groupRepository;
    private final FcmGroupService fcmGroupService;

    private final Map<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Seoul");

    @PostConstruct
    public void init() {
        log.info("⏰ 서버 기동: 그룹 푸시 스케줄 재등록 시작");
        groupRepository.findAll().forEach(g -> {
            String raw = g.getSchedule();
            if (raw == null || raw.isBlank()) return;
            try {
                String cron = tryAsCron(raw);   // 자연어면 CRON으로 변환
                register(g.getId(), cron);
            } catch (Exception e) {
                log.warn("⚠️ 스케줄 등록 실패: groupId={}, value='{}', reason={}", g.getId(), raw, e.getMessage());
            }
        });
        log.info("⏰ 서버 기동: 스케줄 재등록 완료 (총 {}건)", jobs.size());
    }

    private String tryAsCron(String value) {
        // 1) 이미 CRON인지 검증
        try { new CronTrigger(value, TZ); return value; } catch (Exception ignore) {}
        // 2) 아니면 자연어 파싱
        return KoreanScheduleParser.toCron(value);
    }

    public void register(Long groupId, String cron) {
        unregister(groupId);
        try {
            ScheduledFuture<?> f = scheduler.schedule(
                    () -> {
                        try {
                            log.info("⏰ 스케줄 실행: groupId={}", groupId);

                            // 🔹 그룹명 조회 (roomTitle 사용)
                            var groupOpt = groupRepository.findById(groupId);
                            String groupName = groupOpt.map(g -> g.getRoomTitle()).orElse("모임");

                            // 🔹 MEETING_START 타입 + groupId 포함
                            fcmGroupService.sendGroupNow(
                                    groupId,
                                    groupName,                  // title = 그룹 이름
                                    "",                         // body는 빈값 (클라에서 "모임 시작" 처리)
                                    null,                    // 채널 ID 예시
                                    Map.of(
                                            "type", "MEETING_START",
                                            "groupId", String.valueOf(groupId)
                                    )
                            );
                        } catch (Exception e) {
                            log.error("❌ 스케줄 실행 실패: groupId={}, error={}", groupId, e.getMessage(), e);
                        }
                    },
                    new CronTrigger(cron, TZ)
            );
            jobs.put(groupId, f);
            log.info("✅ 스케줄 등록 완료: groupId={}, cron={}", groupId, cron);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ 잘못된 CRON 표현식: groupId={}, value='{}' (예: 월요일 9시 0분 → 0 0 9 * * MON)", groupId, cron);
        }
    }


    public void unregister(Long groupId) {
        ScheduledFuture<?> f = jobs.remove(groupId);
        if (f != null) {
            f.cancel(false);
            log.info("🗑️ 스케줄 해제 완료: groupId={}", groupId);
        }
    }
}
