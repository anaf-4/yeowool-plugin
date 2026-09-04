package com.yeowool.admin.itemtool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void bareNumberIsMinutes() {
        assertEquals(30 * 60_000L, DurationParser.parseToMillis("30"));
    }

    @Test
    void singleUnitSuffixes() {
        assertEquals(45_000L, DurationParser.parseToMillis("45s"));
        assertEquals(30 * 60_000L, DurationParser.parseToMillis("30m"));
        assertEquals(2 * 3_600_000L, DurationParser.parseToMillis("2h"));
        assertEquals(7 * 86_400_000L, DurationParser.parseToMillis("7d"));
    }

    @Test
    void combinedSegmentsSum() {
        assertEquals(86_400_000L + 12 * 3_600_000L, DurationParser.parseToMillis("1d12h"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(2 * 3_600_000L, DurationParser.parseToMillis("2H"));
    }

    @Test
    void rejectsGarbageAndTrailingJunk() {
        assertTrue(DurationParser.parseToMillis("abc") < 0);
        assertTrue(DurationParser.parseToMillis("2hx") < 0);
        assertTrue(DurationParser.parseToMillis("") < 0);
    }

    @Test
    void rejectsZeroDuration() {
        assertTrue(DurationParser.parseToMillis("0m") < 0);
    }
}
