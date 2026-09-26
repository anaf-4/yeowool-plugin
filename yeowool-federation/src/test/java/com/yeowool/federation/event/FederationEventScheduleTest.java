package com.yeowool.federation.event;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationEventScheduleTest {

    // Saturday 20:00 for 24 hours. 2026-09-26 is a Saturday in ISO week 2026-W39.
    private final FederationEventSchedule schedule =
            new FederationEventSchedule(DayOfWeek.SATURDAY, LocalTime.of(20, 0), 1440);

    private static ZonedDateTime at(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void insideTheWindowOnTheStartDay() {
        var window = schedule.activeWindow(at(9, 26, 21)).orElseThrow();

        assertEquals("2026-W39", window.weekKey());
        assertEquals(at(9, 26, 20).toInstant().toEpochMilli(), window.startMillis());
        assertEquals(at(9, 27, 20).toInstant().toEpochMilli(), window.endMillis());
    }

    @Test
    void windowSpanningMidnightStillBelongsToTheStartWeek() {
        var window = schedule.activeWindow(at(9, 27, 10)).orElseThrow();

        assertEquals("2026-W39", window.weekKey());
    }

    @Test
    void beforeTheStartTimeOnTheStartDayIsOutside() {
        assertTrue(schedule.activeWindow(at(9, 26, 19)).isEmpty());
    }

    @Test
    void endIsExclusive() {
        assertTrue(schedule.activeWindow(at(9, 27, 20)).isEmpty());
    }

    @Test
    void midweekIsOutside() {
        assertTrue(schedule.activeWindow(at(9, 30, 12)).isEmpty());
    }
}
