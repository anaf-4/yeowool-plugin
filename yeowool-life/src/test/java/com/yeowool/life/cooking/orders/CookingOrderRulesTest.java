package com.yeowool.life.cooking.orders;

import com.yeowool.life.cooking.orders.CookingOrderRules.Difficulty;
import com.yeowool.life.cooking.orders.CookingOrderRules.Draw;
import com.yeowool.life.cooking.orders.CookingOrderRules.Recipe;
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

class CookingOrderRulesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static CookingOrderRules rules(int vipChance, Map<String, CookingOrderRules.RecipeOverride> overrides) {
        Map<Difficulty, CookingOrderRules.Tier> tiers = new EnumMap<>(Difficulty.class);
        tiers.put(Difficulty.EASY, new CookingOrderRules.Tier(3, 6, 1500, 1, 1));
        tiers.put(Difficulty.NORMAL, new CookingOrderRules.Tier(2, 4, 4000, 2, 2));
        tiers.put(Difficulty.HARD, new CookingOrderRules.Tier(1, 3, 10000, 3, 3));
        return new CookingOrderRules(new CookingOrderRules.Settings(3, tiers, List.of(1.0, 1.5, 3.0), 5000, 3, 5000, 20,
                new CookingOrderRules.Vip(vipChance, 1, 2, 5, 5, 5),
                new CookingOrderRules.Group(List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), LocalTime.of(19, 0), 72,
                        300, 150, 0.5, 5, 5, List.of(15L, 10L, 5L)),
                List.of(new CookingOrderRules.FameLevel("견습", 0, 1.0), new CookingOrderRules.FameLevel("요리사", 30, 1.1),
                        new CookingOrderRules.FameLevel("숙련", 100, 1.2), new CookingOrderRules.FameLevel("명셰프", 250, 1.35),
                        new CookingOrderRules.FameLevel("여울 명장", 500, 1.5)),
                Map.of(), overrides));
    }

    private static CookingOrderRules rules() {
        return rules(10, Map.of());
    }

    private static ZonedDateTime at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZONE); // 2026-09-28 is a Monday
    }

    @Test
    void difficultyFollowsStageCountAndOverrides() {
        assertEquals(Difficulty.EASY, CookingOrderRules.byStages(1));
        assertEquals(Difficulty.EASY, CookingOrderRules.byStages(0));
        assertEquals(Difficulty.NORMAL, CookingOrderRules.byStages(2));
        assertEquals(Difficulty.HARD, CookingOrderRules.byStages(3));
        assertEquals(Difficulty.HARD, CookingOrderRules.byStages(5));
        CookingOrderRules overridden = rules(10, Map.of(
                "ramen", new CookingOrderRules.RecipeOverride(Difficulty.NORMAL, 3000),
                "soup", new CookingOrderRules.RecipeOverride(null, 2222)));
        assertEquals(Difficulty.NORMAL, overridden.difficulty(new Recipe("ramen", 1, 3)));
        assertEquals(3000, overridden.moneyPerDish("ramen", Difficulty.NORMAL));
        assertEquals(Difficulty.HARD, overridden.difficulty(new Recipe("soup", 4, 1)));
        assertEquals(2222, overridden.moneyPerDish("soup", Difficulty.HARD));
        assertEquals(10000, overridden.moneyPerDish("other", Difficulty.HARD));
    }

    @Test
    void drawUsesOnlyLearnedRecipesWithoutRepeats() {
        List<Recipe> learned = List.of(new Recipe("a", 1, 1), new Recipe("b", 2, 1), new Recipe("c", 3, 1), new Recipe("d", 1, 1));
        for (int seed = 0; seed < 200; seed++) {
            List<Draw> orders = rules().draw(new Random(seed), learned);
            assertEquals(3, orders.size());
            Set<String> ids = new HashSet<>();
            for (Draw order : orders) {
                assertTrue(learned.stream().anyMatch(r -> r.id().equals(order.recipeId())));
                assertTrue(ids.add(order.recipeId()), "repeat in " + orders);
                assertFalse(order.vip()); // nothing learned has a 금 tier
                CookingOrderRules.Tier tier = rules().tier(order.difficulty());
                assertTrue(order.required() >= tier.amountMin() && order.required() <= tier.amountMax());
            }
        }
    }

    @Test
    void drawRepeatsOnlyWhenTooFewLearned() {
        List<Draw> orders = rules().draw(new Random(1), List.of(new Recipe("a", 1, 1), new Recipe("b", 1, 1)));
        assertEquals(3, orders.size());
        assertEquals(Set.of("a", "b"), new HashSet<>(orders.stream().map(Draw::recipeId).toList()));
        assertTrue(rules().draw(new Random(1), List.of()).isEmpty());
    }

    @Test
    void vipOnlyFromRecipesWithGoldenTier() {
        List<Recipe> learned = List.of(new Recipe("plain1", 1, 1), new Recipe("plain2", 1, 2), new Recipe("fancy", 2, 3));
        for (int seed = 0; seed < 100; seed++) {
            List<Draw> orders = rules(100, Map.of()).draw(new Random(seed), learned);
            List<Draw> vips = orders.stream().filter(Draw::vip).toList();
            assertEquals(1, vips.size());
            assertEquals("fancy", vips.get(0).recipeId());
            assertTrue(vips.get(0).required() >= 1 && vips.get(0).required() <= 2);
            assertEquals(1, orders.stream().filter(o -> o.recipeId().equals("fancy")).count());
        }
        assertTrue(rules(0, Map.of()).draw(new Random(3), learned).stream().noneMatch(Draw::vip));
        List<Recipe> noGolden = List.of(new Recipe("x", 1, 1), new Recipe("y", 1, 2));
        assertTrue(rules(100, Map.of()).draw(new Random(3), noGolden).stream().noneMatch(Draw::vip));
    }

    @Test
    void vipChanceIsRoughlyTheConfiguredPercent() {
        List<Recipe> learned = List.of(new Recipe("a", 1, 3), new Recipe("b", 1, 3), new Recipe("c", 1, 3));
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
        List<Recipe> learned = List.of(new Recipe("a", 1, 1), new Recipe("b", 1, 1), new Recipe("c", 1, 1), new Recipe("d", 1, 1));
        for (int seed = 0; seed < 50; seed++) {
            Optional<Draw> draw = rules().drawReplacement(new Random(seed), learned, Set.of("a", "b", "c"));
            assertEquals("d", draw.orElseThrow().recipeId());
            assertFalse(draw.get().vip());
        }
        assertTrue(rules().drawReplacement(new Random(1), List.of(), Set.of()).isEmpty());
    }

    @Test
    void rewardMathAppliesQualityFameAndVip() {
        CookingOrderRules rules = rules();
        assertEquals(1500, rules.dishMoney(1500, CookingOrderRules.NORMAL, false, 1.0));
        assertEquals(2250, rules.dishMoney(1500, CookingOrderRules.SILVER, false, 1.0));
        assertEquals(4500, rules.dishMoney(1500, CookingOrderRules.GOLDEN, false, 1.0));
        assertEquals(6075, rules.dishMoney(1500, CookingOrderRules.GOLDEN, false, 1.35));
        // VIP: ×5 instead of the 금 ×3, times fame
        assertEquals(50000, rules.dishMoney(10000, CookingOrderRules.GOLDEN, true, 1.0));
        assertEquals(75000, rules.dishMoney(10000, CookingOrderRules.GOLDEN, true, 1.5));
        // group: half, quality, no fame
        assertEquals(750, rules.groupDishMoney(1500, CookingOrderRules.NORMAL));
        assertEquals(6000, rules.groupDishMoney(4000, CookingOrderRules.GOLDEN));
    }

    @Test
    void fameLevels() {
        CookingOrderRules rules = rules();
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
        CookingOrderRules rules = rules();
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
        CookingOrderRules rules = rules();
        assertEquals(300, rules.groupTarget(Difficulty.EASY));
        assertEquals(150, rules.groupTarget(Difficulty.NORMAL));
        List<Recipe> all = List.of(new Recipe("hard", 3, 3), new Recipe("easy", 1, 1));
        for (int seed = 0; seed < 50; seed++) {
            assertEquals("easy", rules.pickGroupRecipe(new Random(seed), all).orElseThrow().id());
        }
        assertTrue(rules.pickGroupRecipe(new Random(1), List.of(new Recipe("hard", 3, 3))).isEmpty());
    }
}
