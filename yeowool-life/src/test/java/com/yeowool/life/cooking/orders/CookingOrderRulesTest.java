package com.yeowool.life.cooking.orders;

import com.yeowool.life.orders.OrderRules;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Difficulty;
import com.yeowool.life.orders.OrderRules.Draw;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 요리 주문 on the shared order engine: the same numbers and behavior as before the engine was extracted. */
class CookingOrderRulesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static OrderRules rules(int vipChance, Map<String, OrderRules.Override> overrides) {
        Map<Difficulty, OrderRules.Tier> tiers = new EnumMap<>(Difficulty.class);
        tiers.put(Difficulty.EASY, new OrderRules.Tier(3, 6, 1500, 1, 1, 0));
        tiers.put(Difficulty.NORMAL, new OrderRules.Tier(2, 4, 4000, 2, 2, 0));
        tiers.put(Difficulty.HARD, new OrderRules.Tier(1, 3, 10000, 3, 3, 0));
        return new OrderRules(new OrderRules.Settings(3, tiers, List.of(1.0, 1.5, 3.0), 5000, 3, 5000, 20,
                new OrderRules.Vip(vipChance, 1, 2, 5, 5, 5, 0),
                new OrderRules.Group(List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), LocalTime.of(19, 0), 72,
                        300, 150, 0.5, 5, 5, List.of(15L, 10L, 5L)),
                List.of(new OrderRules.FameLevel("견습", 0, 1.0), new OrderRules.FameLevel("요리사", 30, 1.1),
                        new OrderRules.FameLevel("숙련", 100, 1.2), new OrderRules.FameLevel("명셰프", 250, 1.35),
                        new OrderRules.FameLevel("여울 명장", 500, 1.5)),
                Map.of(), overrides));
    }

    private static OrderRules rules() {
        return rules(10, Map.of());
    }

    private static Candidate recipe(String id, int stages, int qualities) {
        return CookingOrderCatalog.candidate(rules(), id, stages, qualities);
    }

    private static double multiplier(OrderRules rules, int quality) {
        return rules.settings().itemMultipliers().get(quality);
    }

    private static long dishMoney(OrderRules rules, long base, int quality, boolean vip, double fame) {
        return rules.itemMoney(base, multiplier(rules, quality), vip, fame);
    }

    private static ZonedDateTime at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZONE); // 2026-09-28 is a Monday
    }

    @Test
    void difficultyFollowsStageCountAndOverrides() {
        assertEquals(Difficulty.EASY, CookingOrderCatalog.byStages(1));
        assertEquals(Difficulty.EASY, CookingOrderCatalog.byStages(0));
        assertEquals(Difficulty.NORMAL, CookingOrderCatalog.byStages(2));
        assertEquals(Difficulty.HARD, CookingOrderCatalog.byStages(3));
        assertEquals(Difficulty.HARD, CookingOrderCatalog.byStages(5));
        OrderRules overridden = rules(10, Map.of(
                "ramen", new OrderRules.Override(Difficulty.NORMAL, 3000),
                "soup", new OrderRules.Override(null, 2222)));
        assertEquals(Difficulty.NORMAL, CookingOrderCatalog.candidate(overridden, "ramen", 1, 3).difficulty());
        assertEquals(3000, overridden.moneyPerItem("ramen", Difficulty.NORMAL));
        assertEquals(Difficulty.HARD, CookingOrderCatalog.candidate(overridden, "soup", 4, 1).difficulty());
        assertEquals(2222, overridden.moneyPerItem("soup", Difficulty.HARD));
        assertEquals(10000, overridden.moneyPerItem("other", Difficulty.HARD));
    }

    @Test
    void drawUsesOnlyLearnedRecipesWithoutRepeats() {
        List<Candidate> learned = List.of(recipe("a", 1, 1), recipe("b", 2, 1), recipe("c", 3, 1), recipe("d", 1, 1));
        for (int seed = 0; seed < 200; seed++) {
            List<Draw> orders = rules().draw(new Random(seed), learned);
            assertEquals(3, orders.size());
            Set<String> ids = new HashSet<>();
            for (Draw order : orders) {
                assertTrue(learned.stream().anyMatch(r -> r.id().equals(order.itemId())));
                assertTrue(ids.add(order.itemId()), "repeat in " + orders);
                assertFalse(order.vip()); // nothing learned has a 금 tier
                OrderRules.Tier tier = rules().tier(order.difficulty());
                assertTrue(order.required() >= tier.amountMin() && order.required() <= tier.amountMax());
            }
        }
    }

    @Test
    void drawRepeatsOnlyWhenTooFewLearned() {
        List<Draw> orders = rules().draw(new Random(1), List.of(recipe("a", 1, 1), recipe("b", 1, 1)));
        assertEquals(3, orders.size());
        assertEquals(Set.of("a", "b"), new HashSet<>(orders.stream().map(Draw::itemId).toList()));
        assertTrue(rules().draw(new Random(1), List.of()).isEmpty());
    }

    @Test
    void vipOnlyFromRecipesWithGoldenTier() {
        List<Candidate> learned = List.of(recipe("plain1", 1, 1), recipe("plain2", 1, 2), recipe("fancy", 2, 3));
        for (int seed = 0; seed < 100; seed++) {
            List<Draw> orders = rules(100, Map.of()).draw(new Random(seed), learned);
            List<Draw> vips = orders.stream().filter(Draw::vip).toList();
            assertEquals(1, vips.size());
            assertEquals("fancy", vips.get(0).itemId());
            assertTrue(vips.get(0).required() >= 1 && vips.get(0).required() <= 2);
            assertEquals(1, orders.stream().filter(o -> o.itemId().equals("fancy")).count());
        }
        assertTrue(rules(0, Map.of()).draw(new Random(3), learned).stream().noneMatch(Draw::vip));
        List<Candidate> noGolden = List.of(recipe("x", 1, 1), recipe("y", 1, 2));
        assertTrue(rules(100, Map.of()).draw(new Random(3), noGolden).stream().noneMatch(Draw::vip));
    }

    @Test
    void vipChanceIsRoughlyTheConfiguredPercent() {
        List<Candidate> learned = List.of(recipe("a", 1, 3), recipe("b", 1, 3), recipe("c", 1, 3));
        Random random = new Random(42);
        int vip = 0;
        for (int i = 0; i < 10_000; i++) {
            if (rules().draw(random, learned).stream().anyMatch(Draw::vip)) {
                vip++;
            }
        }
        assertTrue(vip > 800 && vip < 1200, "vip draws " + vip);
    }

    @Test
    void replacementAvoidsTodaysRecipes() {
        List<Candidate> learned = List.of(recipe("a", 1, 1), recipe("b", 1, 1), recipe("c", 1, 1), recipe("d", 1, 1));
        for (int seed = 0; seed < 50; seed++) {
            Optional<Draw> draw = rules().drawReplacement(new Random(seed), learned, Set.of("a", "b", "c"));
            assertEquals("d", draw.orElseThrow().itemId());
            assertFalse(draw.get().vip());
        }
        assertTrue(rules().drawReplacement(new Random(1), List.of(), Set.of()).isEmpty());
    }

    @Test
    void rewardMathAppliesQualityFameAndVip() {
        OrderRules rules = rules();
        assertEquals(1500, dishMoney(rules, 1500, OrderRules.NORMAL, false, 1.0));
        assertEquals(2250, dishMoney(rules, 1500, OrderRules.SILVER, false, 1.0));
        assertEquals(4500, dishMoney(rules, 1500, OrderRules.GOLDEN, false, 1.0));
        assertEquals(6075, dishMoney(rules, 1500, OrderRules.GOLDEN, false, 1.35));
        // VIP: ×5 instead of the 금 ×3, times fame
        assertEquals(50000, dishMoney(rules, 10000, OrderRules.GOLDEN, true, 1.0));
        assertEquals(75000, dishMoney(rules, 10000, OrderRules.GOLDEN, true, 1.5));
        // group: half, quality, no fame
        assertEquals(750, rules.groupItemMoney(1500, multiplier(rules, OrderRules.NORMAL)));
        assertEquals(6000, rules.groupItemMoney(4000, multiplier(rules, OrderRules.GOLDEN)));
    }

    @Test
    void fameLevels() {
        OrderRules rules = rules();
        assertEquals("견습", rules.level(0).name());
        assertEquals("견습", rules.level(29).name());
        assertEquals("요리사", rules.level(30).name());
        assertEquals("명셰프", rules.level(499).name());
        assertEquals("여울 명장", rules.level(9999).name());
        assertEquals(30, rules.nextLevel(0).orElseThrow().fame());
        assertEquals(500, rules.nextLevel(250).orElseThrow().fame());
        assertTrue(rules.nextLevel(500).isEmpty());
    }

    @Test
    void groupScheduleFindsCurrentAndNextStart() {
        OrderRules rules = rules();
        assertEquals(at(9, 28, 19, 0), rules.nextGroupStart(at(9, 28, 18, 59)).orElseThrow());
        assertEquals(at(10, 1, 19, 0), rules.nextGroupStart(at(9, 28, 19, 0)).orElseThrow());
        assertEquals(at(10, 1, 19, 0), rules.nextGroupStart(at(9, 29, 10, 0)).orElseThrow());
        assertEquals(at(10, 5, 19, 0), rules.nextGroupStart(at(10, 2, 0, 0)).orElseThrow());

        assertTrue(rules.currentGroupStart(at(9, 28, 18, 59)).isEmpty());
        assertEquals(at(9, 28, 19, 0), rules.currentGroupStart(at(9, 28, 19, 0)).orElseThrow());
        assertEquals(at(9, 28, 19, 0), rules.currentGroupStart(at(9, 30, 12, 0)).orElseThrow());
        // Monday's 72 h end exactly when Thursday's begins
        assertEquals(at(10, 1, 19, 0), rules.currentGroupStart(at(10, 1, 19, 0)).orElseThrow());
        assertEquals(at(10, 1, 19, 0), rules.currentGroupStart(at(10, 4, 18, 59)).orElseThrow());
        assertTrue(rules.currentGroupStart(at(10, 4, 19, 0)).isEmpty());
    }

    @Test
    void groupTargetAndRecipeSkipHard() {
        OrderRules rules = rules();
        assertEquals(300, rules.groupTarget(Difficulty.EASY));
        assertEquals(150, rules.groupTarget(Difficulty.NORMAL));
        List<Candidate> all = List.of(recipe("hard", 3, 3), recipe("easy", 1, 1));
        for (int seed = 0; seed < 50; seed++) {
            assertEquals("easy", rules.pickGroupItem(new Random(seed), all).orElseThrow().id());
        }
        assertTrue(rules.pickGroupItem(new Random(1), List.of(recipe("hard", 3, 3))).isEmpty());
    }
}
