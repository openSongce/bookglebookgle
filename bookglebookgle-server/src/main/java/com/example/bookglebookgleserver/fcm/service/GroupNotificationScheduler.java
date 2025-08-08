package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.*;

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
            if (g.getSchedule() != null && !g.getSchedule().isBlank()) {
                register(g.getId(), g.getSchedule());
            }
        });
        log.info("⏰ 서버 기동: 스케줄 재등록 완료 (총 {}건)", jobs.size());
    }

    public void register(Long groupId, String cron) {
        unregister(groupId);
        ScheduledFuture<?> f = scheduler.schedule(
                () -> {
                    try {
                        log.info("⏰ 스케줄 실행: groupId={}", groupId);
                        fcmGroupService.sendGroupNow(
                                groupId,
                                "북글북글 리마인드",
                                "함께 읽을 시간이에요 📚",
                                "default",
                                Map.of("groupId", String.valueOf(groupId))
                        );
                    } catch (Exception e) {
                        log.error("❌ 스케줄 실행 실패: groupId={}, error={}", groupId, e.getMessage(), e);
                    }
                },
                new CronTrigger(cron, TZ)
        );
        jobs.put(groupId, f);
        log.info("✅ 스케줄 등록 완료: groupId={}, cron={}", groupId, cron);
    }

    public void unregister(Long groupId) {
        ScheduledFuture<?> f = jobs.remove(groupId);
        if (f != null) {
            f.cancel(false);
            log.info("🗑️ 스케줄 해제 완료: groupId={}", groupId);
        }
    }
}
