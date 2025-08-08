package com.example.bookglebookgleserver.fcm.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KoreanScheduleParser {
    private KoreanScheduleParser() {}

    private static final Map<String, String> DAY = Map.of(
            "월","MON","화","TUE","수","WED","목","THU","금","FRI","토","SAT","일","SUN"
    );

    // "월요일 9시", "월요일 9시 0분", "매주 월요일 21시 30분", "월 9:00", "월요일 오후 9시 30분" 등
    private static final Pattern P1 = Pattern.compile(
            "^(?:매주\\s*)?(월|화|수|목|금|토|일)(?:요일)?\\s*(오전|오후)?\\s*(\\d{1,2})\\s*(?:시)?\\s*(?:(\\d{1,2})\\s*분?)?$"
    );
    private static final Pattern P2 = Pattern.compile(
            "^(?:매주\\s*)?(월|화|수|목|금|토|일)(?:요일)?\\s*(\\d{1,2}):(\\d{2})$"
    );

    public static String toCron(String input) {
        if (input == null) return null;
        String s = input.trim().replaceAll("\\s+", " ");
        Matcher m1 = P1.matcher(s);
        if (m1.find()) {
            String dayKo = m1.group(1);
            String ampm  = m1.group(2); // 오전/오후 or null
            int hour     = Integer.parseInt(m1.group(3));
            int minute   = m1.group(4) == null ? 0 : Integer.parseInt(m1.group(4));
            return buildCron(dayKo, ampm, hour, minute);
        }
        Matcher m2 = P2.matcher(s);
        if (m2.find()) {
            String dayKo = m2.group(1);
            int hour     = Integer.parseInt(m2.group(2));
            int minute   = Integer.parseInt(m2.group(3));
            return buildCron(dayKo, null, hour, minute);
        }
        throw new IllegalArgumentException("지원하지 않는 스케줄 형식: " + input);
    }

    private static String buildCron(String dayKo, String ampm, int hour, int minute) {
        String dow = DAY.get(dayKo);
        if (dow == null) throw new IllegalArgumentException("요일 인식 실패: " + dayKo);

        if (ampm != null) { // 오전/오후 처리
            if ("오전".equals(ampm)) hour = (hour == 12) ? 0 : hour;
            else if ("오후".equals(ampm)) hour = (hour == 12) ? 12 : hour + 12;
        }
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("시(hour)는 0~23");
        if (minute < 0 || minute > 59) throw new IllegalArgumentException("분(minute)은 0~59");

        // Spring 6필드 CRON: 초 분 시 일 월 요일
        return String.format(Locale.ROOT, "0 %d %d * * %s", minute, hour, dow);
    }
}
