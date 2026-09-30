package com.yeowool.life.donation;

import com.yeowool.life.donation.DonationRepository.Contribution;
import com.yeowool.life.donation.DonationRepository.Goal;
import com.yeowool.life.donation.DonationRules.Candidate;
import com.yeowool.life.donation.DonationRules.GoalSpec;
import com.yeowool.life.surprise.SurpriseEventType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonationRulesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, SEOUL);
    }

    // ---- weekly start / deadline ----

    @Test
    void periodStartIsTheLatestMondayMidnight() {
        // 2026-09-30 is a Wednesday
        assertEquals(at(2026, 9, 28, 0, 0), DonationRules.periodStart(at(2026, 9, 30, 15, 30), DayOfWeek.MONDAY));
        // Monday 00:00 exactly starts a new week; one minute before still belongs to the previous one
        assertEquals(at(2026, 10, 5, 0, 0), DonationRules.periodStart(at(2026, 10, 5, 0, 0), DayOfWeek.MONDAY));
        assertEquals(at(2026, 9, 28, 0, 0), DonationRules.periodStart(at(2026, 10, 4, 23, 59), DayOfWeek.MONDAY));
        assertEquals(at(2026, 10, 5, 0, 0), DonationRules.nextPeriodStart(at(2026, 9, 30, 15, 30), DayOfWeek.MONDAY));
    }

    @Test
    void staffStartEndsAtThisWeeksDeadlineOrAfterAFullDurationIfItPassed() {
        assertEquals(at(2026, 10, 5, 0, 0), DonationRules.endFor(at(2026, 9, 30, 15, 30), DayOfWeek.MONDAY, 7));
        // a 5-day project already closed on Saturday: a staff start then runs 5 days from now
        assertEquals(at(2026, 10, 8, 12, 0), DonationRules.endFor(at(2026, 10, 3, 12, 0), DayOfWeek.MONDAY, 5));
    }

    // ---- candidates & buffs ----

    private static Candidate candidate(String name) {
        return new Candidate(name, null, List.of(new GoalSpec("WHEAT", null, 10, 1)));
    }

    @Test
    void pickAvoidsLastWeeksProjectWhenThereIsAChoice() {
        List<Candidate> pool = List.of(candidate("A"), candidate("B"), candidate("C"));
        Set<String> seen = new HashSet<>();
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            String picked = DonationRules.pick(random, pool, "B").orElseThrow().name();
            assertNotEquals("B", picked);
            seen.add(picked);
        }
        assertEquals(Set.of("A", "C"), seen);
        assertEquals("B", DonationRules.pick(new Random(1), List.of(candidate("B")), "B").orElseThrow().name());
        assertTrue(DonationRules.pick(new Random(1), List.of(), null).isEmpty());
    }

    @Test
    void buffRotatesThroughTheConfiguredOrder() {
        List<SurpriseEventType> order = List.of(SurpriseEventType.LAND_XP, SurpriseEventType.CROP_DROP, SurpriseEventType.JOB_XP);
        assertEquals(SurpriseEventType.LAND_XP, DonationRules.nextBuff(order, null));
        assertEquals(SurpriseEventType.CROP_DROP, DonationRules.nextBuff(order, SurpriseEventType.LAND_XP));
        assertEquals(SurpriseEventType.LAND_XP, DonationRules.nextBuff(order, SurpriseEventType.JOB_XP));
        assertEquals(SurpriseEventType.LAND_XP, DonationRules.nextBuff(order, SurpriseEventType.TREASURE_DROP));
        assertEquals(3.0, DonationRules.buffMultiplier(SurpriseEventType.TREASURE_DROP));
        assertEquals(2.0, DonationRules.buffMultiplier(SurpriseEventType.JOB_XP));
    }

    // ---- donating ----

    @Test
    void donationIsClampedToWhatIsLeft() {
        assertEquals(64, DonationRules.clamp(64, 1000, 0));
        assertEquals(10, DonationRules.clamp(64, 1000, 990));
        assertEquals(0, DonationRules.clamp(64, 1000, 1000));
        assertEquals(0, DonationRules.clamp(64, 1000, 1200)); // never negative
    }

    private static Goal goal(int target, int progress) {
        return new Goal(0, "WHEAT", null, target, progress, 1);
    }

    @Test
    void overallPercentAveragesGoalsAndOnlyAllDoneIsComplete() {
        List<Goal> half = List.of(goal(100, 100), goal(1000, 0));
        assertEquals(50, DonationRules.overallPercent(half));
        assertFalse(DonationRules.allComplete(half));
        List<Goal> almost = List.of(goal(100, 100), goal(1000, 999));
        assertEquals(99, DonationRules.overallPercent(almost)); // floored — never shows 100 early
        assertFalse(DonationRules.allComplete(almost));
        List<Goal> done = List.of(goal(100, 100), goal(1000, 1000));
        assertEquals(100, DonationRules.overallPercent(done));
        assertTrue(DonationRules.allComplete(done));
        assertFalse(DonationRules.allComplete(List.of()));
    }

    @Test
    void milestoneIsTheHighestReachedBelowFull() {
        assertEquals(OptionalInt.empty(), DonationRules.milestone(49));
        assertEquals(OptionalInt.of(50), DonationRules.milestone(50));
        assertEquals(OptionalInt.of(50), DonationRules.milestone(89));
        assertEquals(OptionalInt.of(90), DonationRules.milestone(95));
        assertEquals(OptionalInt.empty(), DonationRules.milestone(100));
    }

    // ---- score & rewards ----

    @Test
    void rewardsGiveParticipationAboveMinScoreAndRankBonuses() {
        List<Contribution> ranked = List.of(
                new Contribution(UUID.randomUUID(), "first", 5000),
                new Contribution(UUID.randomUUID(), "second", 800),
                new Contribution(UUID.randomUUID(), "third", 100),
                new Contribution(UUID.randomUUID(), "fourth", 100),
                new Contribution(UUID.randomUUID(), "fifth", 99));
        assertEquals(List.of(35L, 25L, 15L, 5L, 0L), DonationRules.rewards(ranked, 100, 5, List.of(30L, 20L, 10L)));
        // a rank bonus also needs min-score: 2nd and 3rd are below 1000, so they get nothing
        assertEquals(List.of(35L, 0L, 0L, 0L, 0L), DonationRules.rewards(ranked, 1000, 5, List.of(30L, 20L, 10L)));
    }

    @Test
    void progressBarFillsProportionally() {
        assertEquals("□□□□□□□□□□", DonationRules.bar(0, 100, 10));
        assertEquals("■■■■■□□□□□", DonationRules.bar(50, 100, 10));
        assertEquals("■■■■■■■■■■", DonationRules.bar(100, 100, 10));
        assertEquals("■■■■■■■■■■", DonationRules.bar(150, 100, 10));
    }
}
