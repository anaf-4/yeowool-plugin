package com.yeowool.community.vote;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoteRulesTest {

    private static final List<Integer> THRESHOLDS = List.of(0, 100, 10, 30);

    @Test
    void nameKeyTrimsLowercasesAndRejectsNonNames() {
        assertEquals("steve_1", VoteRules.nameKey("  Steve_1 "));
        assertNull(VoteRules.nameKey(null));
        assertNull(VoteRules.nameKey("   "));
        assertNull(VoteRules.nameKey("two words"));
        assertNull(VoteRules.nameKey("a".repeat(17)));
    }

    @Test
    void nextMilestoneIsSmallestAboveTotalIgnoringEveryVoteRow() {
        assertEquals(OptionalInt.of(10), VoteRules.nextMilestone(THRESHOLDS, 0));
        assertEquals(OptionalInt.of(30), VoteRules.nextMilestone(THRESHOLDS, 10));
        assertEquals(OptionalInt.empty(), VoteRules.nextMilestone(THRESHOLDS, 100));
    }

    @Test
    void crossedIsHalfOpenAndSorted() {
        assertEquals(List.of(10), VoteRules.crossed(THRESHOLDS, 9, 10));
        assertEquals(List.of(), VoteRules.crossed(THRESHOLDS, 10, 11));
        assertEquals(List.of(10, 30, 100), VoteRules.crossed(THRESHOLDS, 0, 100));
        assertEquals(List.of(), VoteRules.crossed(THRESHOLDS, -1, 0));
    }

    @Test
    void monthRangeSpansToNextMonthIncludingYearEnd() {
        assertArrayEquals(new String[]{"2026-09-01", "2026-10-01"}, VoteRules.monthRange(YearMonth.of(2026, 9)));
        assertArrayEquals(new String[]{"2026-12-01", "2027-01-01"}, VoteRules.monthRange(YearMonth.of(2026, 12)));
    }

    @Test
    void summaryJoinsNonZeroParts() {
        assertEquals("5,000온 + 별조각 3", VoteRules.summary(5000, 3, 0));
        assertEquals("별조각 20 + 아이템 2개", VoteRules.summary(0, 20, 2));
        assertEquals("없음", VoteRules.summary(0, 0, 0));
    }
}
