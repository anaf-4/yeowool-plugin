package com.yeowool.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationFormatTest {

    @Test
    void zeroIsZeroSeconds() {
        assertEquals("0초", DurationFormat.humanize(0));
    }

    @Test
    void negativeClampsToZero() {
        assertEquals("0초", DurationFormat.humanize(-5000));
    }

    @Test
    void secondsOnly() {
        assertEquals("45초", DurationFormat.humanize(45_000));
    }

    @Test
    void minutesAndSeconds() {
        assertEquals("2분 5초", DurationFormat.humanize(125_000));
    }

    @Test
    void omitsZeroUnitsInTheMiddle() {
        // 1 day, 0 hours, 5 minutes, 0 seconds -> "1일 5분" (hours/seconds skipped, no trailing "0초")
        long ms = 86_400_000L + 5 * 60_000L;
        assertEquals("1일 5분", DurationFormat.humanize(ms));
    }

    @Test
    void daysHoursMinutesSeconds() {
        long ms = 86_400_000L + 3 * 3_600_000L + 20 * 60_000L + 7_000L;
        assertEquals("1일 3시간 20분 7초", DurationFormat.humanize(ms));
    }
}
