package com.yeowool.life.fishing.orders;

import com.yeowool.life.fishing.orders.FishingOrderRules.Fish;
import com.yeowool.life.fishing.orders.FishingOrderRules.Species;
import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Difficulty;
import com.yeowool.life.orders.OrderRules.Draw;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingOrderRulesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final FishingOrderRules.Settings FISH_SETTINGS =
            new FishingOrderRules.Settings(20, 40, 5, 30, 1.3, 0.5, 25, Set.of("vanilla"));

    private static final Species COMMON = new Species("trout", "yw_trout", 60, 15, 40, false);
    private static final Species USUAL = new Species("carp", "yw_carp", 25, 20, 50, false);
    private static final Species RARE = new Species("tuna_fish", "tuna_fish", 15, 15, 50, true);
    private static final Species RARE_NO_SIZE = new Species("eel", "yw_eel", 12, 0, 0, false);
    private static final Species LEGEND = new Species("koi", "yw_koi", 3, 30, 90, false);

    private static FishingOrderRules rules(int vipChance, Map<String, OrderRules.Override> overrides) {
        OrderRules.Settings d = FishingOrderCatalog.DEFAULTS;
        OrderRules.Vip vip = new OrderRules.Vip(vipChance, 1, 1, 5, 5, 5, 150);
        return new FishingOrderRules(FISH_SETTINGS, new OrderRules(new OrderRules.Settings(d.ordersPerDay(), d.tiers(), d.itemMultipliers(),
                d.allDoneMoney(), d.allDoneStardust(), d.rerollCost(), d.stardustDailyCap(), vip, d.group(), d.fameLevels(),
                d.levelUpCommands(), overrides)));
    }

    private static FishingOrderRules rules() {
        return rules(10, Map.of());
    }

    private static Order order(String id, boolean vip, String kind, int minSizeMm) {
        return new Order(0, id, "hard", vip, kind, minSizeMm, 1, 0, false);
    }

    @Test
    void weightGradesAtBoundariesAndOverrides() {
        FishingOrderRules rules = rules();
        assertEquals(Difficulty.EASY, rules.grade(60).orElseThrow());
        assertEquals(Difficulty.EASY, rules.grade(40).orElseThrow());
        assertEquals(Difficulty.NORMAL, rules.grade(39.9).orElseThrow());
        assertEquals(Difficulty.NORMAL, rules.grade(20).orElseThrow());
        assertEquals(Difficulty.HARD, rules.grade(19).orElseThrow());
        assertEquals(Difficulty.HARD, rules.grade(5).orElseThrow());
        assertTrue(rules.grade(4.9).isEmpty());

        Candidate legend = rules.candidate(LEGEND);
        assertTrue(legend.vipOnly());
        assertTrue(legend.vipEligible());
        assertEquals(Difficulty.HARD, legend.difficulty());
        assertFalse(rules.candidate(COMMON).vipEligible());
        assertFalse(rules.candidate(RARE_NO_SIZE).vipEligible()); // 희귀 but neither size nor 금별
        assertTrue(rules.candidate(RARE).vipEligible());

        FishingOrderRules overridden = rules(10, Map.of("koi", new OrderRules.Override(Difficulty.NORMAL, 0),
                "trout", new OrderRules.Override(null, 1234)));
        assertEquals(Difficulty.HARD, overridden.candidate(RARE).difficulty()); // money-only / unrelated overrides keep the grade
        Candidate koi = overridden.candidate(LEGEND);
        assertFalse(koi.vipOnly());
        assertEquals(Difficulty.NORMAL, koi.difficulty());
        assertEquals(Difficulty.EASY, overridden.candidate(COMMON).difficulty());
    }

    @Test
    void drawUsesOnlyOfferedFishWithoutRepeatsAndNeverLegendaryAsNormal() {
        FishingOrderRules fishing = rules();
        OrderRules rules = new OrderRules(FishingOrderCatalog.DEFAULTS);
        List<Candidate> offered = List.of(fishing.candidate(COMMON), fishing.candidate(USUAL), fishing.candidate(RARE),
                fishing.candidate(RARE_NO_SIZE), fishing.candidate(LEGEND));
        for (int seed = 0; seed < 300; seed++) {
            List<Draw> orders = rules.draw(new Random(seed), offered);
            assertEquals(3, orders.size());
            Set<String> ids = new HashSet<>();
            for (Draw order : orders) {
                assertTrue(ids.add(order.itemId()), "repeat in " + orders);
                if (!order.vip()) {
                    assertFalse(order.itemId().equals("koi"), "legendary as a normal order");
                } else {
                    assertTrue(Set.of("tuna_fish", "koi").contains(order.itemId()));
                    assertEquals(1, order.required());
                }
            }
        }
        // only legendary caught → nothing to order
        assertTrue(rules.draw(new Random(1), List.of(fishing.candidate(LEGEND))).isEmpty());
    }

    @Test
    void vipKindFollowsWhatTheSpeciesHas() {
        FishingOrderRules rules = rules();
        Draw vip = new Draw("koi", Difficulty.HARD, true, 1, null, 0);
        Draw big = rules.decorate(new Random(1), vip, LEGEND);
        assertEquals(FishingOrderRules.BIG, big.vipKind());
        assertEquals(750, big.minSizeMm()); // top 25% of 30~90cm → 75cm
        Draw golden = rules.decorate(new Random(1), new Draw("x", Difficulty.HARD, true, 1, null, 0),
                new Species("x", "x", 10, 0, 0, true));
        assertEquals(FishingOrderRules.GOLDEN, golden.vipKind());
        assertEquals(0, golden.minSizeMm());
        Set<String> kinds = new HashSet<>();
        Random random = new Random(3); // one generator, like the service's (first nextInt(2) of fresh small seeds barely varies)
        for (int i = 0; i < 50; i++) {
            kinds.add(rules.decorate(random, new Draw("tuna_fish", Difficulty.HARD, true, 1, null, 0), RARE).vipKind());
        }
        assertEquals(Set.of(FishingOrderRules.BIG, FishingOrderRules.GOLDEN), kinds);
    }

    @Test
    void sizeConditionIsTheMidpointOnAboutThirtyPercent() {
        assertEquals(350, FishingOrderRules.conditionMm(USUAL)); // 20~50cm → 35cm
        assertEquals(325, FishingOrderRules.conditionMm(RARE));   // 15~50cm → 32.5cm
        FishingOrderRules rules = rules();
        Random random = new Random(7);
        int conditioned = 0;
        for (int i = 0; i < 10_000; i++) {
            Draw draw = rules.decorate(random, new Draw("carp", Difficulty.NORMAL, false, 3, null, 0), USUAL);
            if (draw.minSizeMm() > 0) {
                assertEquals(350, draw.minSizeMm());
                assertNull(draw.vipKind());
                conditioned++;
            }
        }
        assertTrue(conditioned > 2700 && conditioned < 3300, "conditioned " + conditioned);
        // no size range → never a size condition
        for (int seed = 0; seed < 100; seed++) {
            assertEquals(0, rules.decorate(new Random(seed), new Draw("eel", Difficulty.HARD, false, 1, null, 0), RARE_NO_SIZE).minSizeMm());
        }
    }

    @Test
    void conditionsDecideWhichFishMatch() {
        Order plain = order("carp", false, null, 0);
        Order sized = order("carp", false, null, 350);
        Order golden = order("tuna_fish", true, FishingOrderRules.GOLDEN, 0);
        assertTrue(FishingOrderRules.matches(plain, new Fish(OrderRules.NORMAL, null)));
        assertTrue(FishingOrderRules.matches(sized, new Fish(OrderRules.NORMAL, 350)));
        assertFalse(FishingOrderRules.matches(sized, new Fish(OrderRules.NORMAL, 349)));
        assertFalse(FishingOrderRules.matches(sized, new Fish(OrderRules.GOLDEN, null))); // unreadable size
        assertTrue(FishingOrderRules.matches(golden, new Fish(OrderRules.GOLDEN, 200)));
        assertFalse(FishingOrderRules.matches(golden, new Fish(OrderRules.SILVER, 900)));
    }

    @Test
    void rewardAppliesSizeBonusStarAndCondition() {
        FishingOrderRules fishing = rules();
        assertEquals(1.0, fishing.sizeBonus(200, USUAL), 1e-9);   // at minimum
        assertEquals(1.25, fishing.sizeBonus(350, USUAL), 1e-9);  // middle
        assertEquals(1.5, fishing.sizeBonus(500, USUAL), 1e-9);   // at maximum
        assertEquals(1.5, fishing.sizeBonus(900, USUAL), 1e-9);   // clamped
        assertEquals(1.0, fishing.sizeBonus(100, USUAL), 1e-9);   // clamped
        assertEquals(1.0, fishing.sizeBonus(null, USUAL), 1e-9);
        assertEquals(1.0, fishing.sizeBonus(300, RARE_NO_SIZE), 1e-9);

        OrderRules rules = new OrderRules(FishingOrderCatalog.DEFAULTS);
        Order plain = order("carp", false, null, 0);
        Order sized = order("carp", false, null, 350);
        // 2,500 × 1.25 size × 1 star = 3,125; × 1.3 condition = 4,062.5 → 4,063; golden ×3; fame ×1.2
        assertEquals(3125, rules.itemMoney(2500, fishing.multiplier(plain, new Fish(OrderRules.NORMAL, 350), USUAL, false), false, 1.0));
        assertEquals(4063, rules.itemMoney(2500, fishing.multiplier(sized, new Fish(OrderRules.NORMAL, 350), USUAL, false), false, 1.0));
        assertEquals(4500, rules.itemMoney(2500, fishing.multiplier(plain, new Fish(OrderRules.SILVER, 200), USUAL, false), false, 1.2));
        assertEquals(11250, rules.itemMoney(2500, fishing.multiplier(plain, new Fish(OrderRules.GOLDEN, 500), USUAL, false), false, 1.0));
        // VIP: hard base × 5 × fame, no size/star
        assertEquals(52500, rules.itemMoney(7000, fishing.multiplier(order("koi", true, "BIG", 750), new Fish(OrderRules.NORMAL, 800), LEGEND, false), true, 1.5));
        // group: half × star only
        assertEquals(400, rules.groupItemMoney(800, fishing.multiplier(plain, new Fish(OrderRules.NORMAL, 500), USUAL, true)));
        assertEquals(1200, rules.groupItemMoney(800, fishing.multiplier(plain, new Fish(OrderRules.GOLDEN, 500), USUAL, true)));
    }

    @Test
    void deliveryTakesSmallestFirstAndStarsLast() {
        List<Fish> fish = new ArrayList<>(List.of(new Fish(OrderRules.GOLDEN, 100), new Fish(OrderRules.NORMAL, 420),
                new Fish(OrderRules.SILVER, 300), new Fish(OrderRules.NORMAL, 210), new Fish(OrderRules.NORMAL, null)));
        fish.sort(FishingOrderRules.DELIVERY_ORDER);
        assertEquals(List.of(new Fish(OrderRules.NORMAL, null), new Fish(OrderRules.NORMAL, 210), new Fish(OrderRules.NORMAL, 420),
                new Fish(OrderRules.SILVER, 300), new Fish(OrderRules.GOLDEN, 100)), fish);
    }

    @Test
    void fameLevelsAndTuesdayFridaySchedule() {
        OrderRules rules = new OrderRules(FishingOrderCatalog.DEFAULTS);
        assertEquals("초보 낚시꾼", rules.level(29).name());
        assertEquals("낚시꾼", rules.level(30).name());
        assertEquals("여울 강태공", rules.level(500).name());
        assertEquals(1.35, rules.level(499).multiplier(), 1e-9);
        // 2026-09-29 is a Tuesday
        assertEquals(at(9, 29, 19, 0), rules.nextGroupStart(at(9, 28, 12, 0)).orElseThrow());
        assertEquals(at(10, 2, 19, 0), rules.nextGroupStart(at(9, 29, 19, 0)).orElseThrow());
        assertEquals(at(9, 29, 19, 0), rules.currentGroupStart(at(10, 2, 18, 59)).orElseThrow());
        assertEquals(at(10, 2, 19, 0), rules.currentGroupStart(at(10, 2, 19, 0)).orElseThrow());
        assertTrue(rules.currentGroupStart(at(9, 29, 18, 59)).isEmpty());
        assertEquals(400, rules.groupTarget(Difficulty.EASY));
        assertEquals(200, rules.groupTarget(Difficulty.NORMAL));
    }

    @Test
    void sizeRangeParsing() {
        assertArrayEquals(new double[]{15, 50}, FishingOrderCatalog.parseRange("15~50"));
        assertArrayEquals(new double[]{15.5, 40}, FishingOrderCatalog.parseRange("15.5~40.0"));
        assertArrayEquals(new double[2], FishingOrderCatalog.parseRange(null));
        assertArrayEquals(new double[2], FishingOrderCatalog.parseRange("abc"));
    }

    private static ZonedDateTime at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZONE);
    }
}
