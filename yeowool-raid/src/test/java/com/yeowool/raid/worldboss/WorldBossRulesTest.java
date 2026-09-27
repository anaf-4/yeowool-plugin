package com.yeowool.raid.worldboss;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBossRulesTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID E = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    @Test
    void topThreeGetRankPlusParticipationAndSmallHittersGetNothing() {
        Map<UUID, Double> damage = new LinkedHashMap<>();
        damage.put(A, 50.0);
        damage.put(B, 300.0);
        damage.put(C, 100.0);
        damage.put(D, 40.0);
        damage.put(E, 4.0); // below 1% of 500
        List<WorldBossRules.Reward> rewards = WorldBossRules.rewards(damage, 500, List.of(100_000L, 50_000L, 30_000L), 5_000, 0.01);
        assertEquals(4, rewards.size());
        assertEquals(new WorldBossRules.Reward(B, 1, 105_000), rewards.get(0));
        assertEquals(new WorldBossRules.Reward(C, 2, 55_000), rewards.get(1));
        assertEquals(new WorldBossRules.Reward(A, 3, 35_000), rewards.get(2));
        assertEquals(new WorldBossRules.Reward(D, 0, 5_000), rewards.get(3));
    }

    @Test
    void emptyDamageGivesNoRewards() {
        assertTrue(WorldBossRules.rewards(Map.of(), 500, List.of(100_000L), 5_000, 0.01).isEmpty());
    }

    @Test
    void topByDamageOrdersAndLimits() {
        Map<UUID, Double> damage = Map.of(A, 1.0, B, 3.0, C, 2.0);
        assertEquals(List.of(B, C), WorldBossRules.topByDamage(damage, 2));
    }

    @Test
    void announceWindowOpensAheadAndClosesAfterTheFight() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalTime spawn = LocalTime.of(21, 0);
        ZonedDateTime early = ZonedDateTime.of(2026, 9, 28, 20, 49, 0, 0, zone);
        ZonedDateTime open = ZonedDateTime.of(2026, 9, 28, 20, 50, 0, 0, zone);
        ZonedDateTime fighting = ZonedDateTime.of(2026, 9, 28, 21, 29, 0, 0, zone);
        ZonedDateTime over = ZonedDateTime.of(2026, 9, 28, 21, 30, 0, 0, zone);
        assertTrue(WorldBossRules.announceDue(early, spawn, 10, 30, null).isEmpty());
        assertEquals(ZonedDateTime.of(2026, 9, 28, 21, 0, 0, 0, zone), WorldBossRules.announceDue(open, spawn, 10, 30, null).orElseThrow());
        assertTrue(WorldBossRules.announceDue(fighting, spawn, 10, 30, "2026-09-27").isPresent());
        assertTrue(WorldBossRules.announceDue(fighting, spawn, 10, 30, "2026-09-28").isEmpty());
        assertTrue(WorldBossRules.announceDue(over, spawn, 10, 30, null).isEmpty());
    }
}
