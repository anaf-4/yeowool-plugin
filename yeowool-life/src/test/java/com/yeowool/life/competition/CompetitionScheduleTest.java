package com.yeowool.life.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitionScheduleTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final List<CompetitionActivity> ROTATION = List.of(
            CompetitionActivity.FISHING, CompetitionActivity.MINING, CompetitionActivity.HUNTING, CompetitionActivity.FARMING);

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
    }

    @Test
    void activityRotatesByEpochDay() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 18, 60, ROTATION);
        LocalDate day = LocalDate.of(2026, 9, 28);
        for (int i = 0; i < 8; i++) {
            LocalDate date = day.plusDays(i);
            assertEquals(ROTATION.get((int) Math.floorMod(date.toEpochDay(), 4L)), schedule.windowFor(date).activity());
        }
        assertEquals(schedule.windowFor(day).activity(), schedule.windowFor(day.plusDays(4)).activity());
    }

    @Test
    void activeOnlyInsideTheWindow() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 18, 60, ROTATION);
        assertTrue(schedule.activeAt(at(2026, 9, 28, 17, 59)).isEmpty());
        Optional<CompetitionSchedule.Window> active = schedule.activeAt(at(2026, 9, 28, 18, 0));
        assertTrue(active.isPresent());
        assertEquals("2026-09-28", active.get().dateKey());
        assertTrue(schedule.activeAt(at(2026, 9, 28, 18, 59)).isPresent());
        assertTrue(schedule.activeAt(at(2026, 9, 28, 19, 0)).isEmpty());
    }

    @Test
    void windowCrossingMidnightBelongsToItsStartDate() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 23, 120, ROTATION);
        Optional<CompetitionSchedule.Window> active = schedule.activeAt(at(2026, 9, 29, 0, 30));
        assertTrue(active.isPresent());
        assertEquals("2026-09-28", active.get().dateKey());
    }

    @Test
    void nextAfterIsTodayBeforeStartOtherwiseTomorrow() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 18, 60, ROTATION);
        assertEquals(LocalDate.of(2026, 9, 28), schedule.nextAfter(at(2026, 9, 28, 10, 0)).date());
        assertEquals(LocalDate.of(2026, 9, 29), schedule.nextAfter(at(2026, 9, 28, 18, 30)).date());
        assertEquals(LocalDate.of(2026, 9, 29), schedule.nextAfter(at(2026, 9, 28, 20, 0)).date());
    }

    @Test
    void emptyRotationFallsBackToAllActivitiesAndBadNumbersAreClamped() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 99, 0, List.of());
        CompetitionSchedule.Window window = schedule.windowFor(LocalDate.of(2026, 9, 28));
        assertEquals(23, window.start().getHour());
        assertEquals(1, java.time.Duration.between(window.start(), window.end()).toMinutes());
    }

    @Test
    void activityKeysParse() {
        assertEquals(Optional.of(CompetitionActivity.MINING), CompetitionActivity.byKey("mining"));
        assertEquals(Optional.of(CompetitionActivity.FARMING), CompetitionActivity.byKey(" FARMING "));
        assertEquals(Optional.empty(), CompetitionActivity.byKey("cooking"));
    }
}
