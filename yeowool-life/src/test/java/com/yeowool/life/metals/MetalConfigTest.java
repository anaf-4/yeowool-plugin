package com.yeowool.life.metals;

import com.yeowool.life.metals.MetalConfig.Metal;
import com.yeowool.life.metals.MetalConfig.Tier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalConfigTest {

    private static MetalConfig shipped() {
        var stream = Objects.requireNonNull(MetalConfigTest.class.getClassLoader().getResourceAsStream("metals.yml"));
        return MetalConfig.load(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)),
                Logger.getLogger("test"));
    }

    /** A Random whose nextDouble() always returns {@code value}. */
    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @Test
    void shippedConfigHas28MetalsInTheSpecSplit() {
        MetalConfig config = shipped();
        assertEquals(28, config.metals().size());
        Map<Tier, Long> counts = config.metals().values().stream().collect(Collectors.groupingBy(Metal::tier, Collectors.counting()));
        assertEquals(Map.of(Tier.COMMON, 10L, Tier.RARE, 9L, Tier.EPIC, 6L, Tier.LEGENDARY, 3L), counts);
        assertEquals(5, config.recipes().size());
        assertEquals(3, config.conversions().size());
        for (String dimension : new String[]{MetalConfig.OVERWORLD, MetalConfig.NETHER}) {
            for (Tier tier : Tier.values()) {
                assertTrue(config.metals().values().stream().anyMatch(m -> m.tier() == tier && m.dimensions().contains(dimension)),
                        tier + " has no metal in " + dimension);
            }
        }
    }

    @Test
    void depthDoublesOnlyEpicAndLegendary() {
        MetalConfig config = shipped();
        assertEquals(70.0, config.weight(Tier.COMMON, -10));
        assertEquals(22.0, config.weight(Tier.RARE, -10));
        assertEquals(14.0, config.weight(Tier.EPIC, -10));
        assertEquals(2.0, config.weight(Tier.LEGENDARY, 0));
        assertEquals(1.0, config.weight(Tier.LEGENDARY, 1));
    }

    @Test
    void pickTierWalksTheWeights() {
        Map<Tier, Double> weights = new EnumMap<>(Tier.class);
        weights.put(Tier.COMMON, 70.0);
        weights.put(Tier.RARE, 22.0);
        weights.put(Tier.EPIC, 7.0);
        weights.put(Tier.LEGENDARY, 1.0);
        assertEquals(Tier.COMMON, MetalConfig.pickTier(fixed(0.0), weights).orElseThrow());
        assertEquals(Tier.COMMON, MetalConfig.pickTier(fixed(0.699), weights).orElseThrow());
        assertEquals(Tier.RARE, MetalConfig.pickTier(fixed(0.70), weights).orElseThrow());
        assertEquals(Tier.EPIC, MetalConfig.pickTier(fixed(0.95), weights).orElseThrow());
        assertEquals(Tier.LEGENDARY, MetalConfig.pickTier(fixed(0.995), weights).orElseThrow());
        assertTrue(MetalConfig.pickTier(fixed(0.5), Map.of()).isEmpty());
    }

    @Test
    void legendaryRateRisesDeepUnderground() {
        MetalConfig config = shipped();
        Random random = new Random(42);
        int n = 200_000;
        int shallow = 0;
        int deep = 0;
        for (int i = 0; i < n; i++) {
            if (config.pick(random, MetalConfig.OVERWORLD, 64).orElseThrow().tier() == Tier.LEGENDARY) {
                shallow++;
            }
            if (config.pick(random, MetalConfig.OVERWORLD, -30).orElseThrow().tier() == Tier.LEGENDARY) {
                deep++;
            }
        }
        assertEquals(0.01, shallow / (double) n, 0.002);
        assertEquals(2.0 / 108, deep / (double) n, 0.002);
    }

    @Test
    void netherPicksOnlyNetherMetals() {
        MetalConfig config = shipped();
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            assertTrue(config.pick(random, MetalConfig.NETHER, 40).orElseThrow().dimensions().contains(MetalConfig.NETHER));
        }
    }

    @Test
    void dropRollUsesPercent() {
        assertTrue(MetalConfig.rollDrop(fixed(0.003), 0.4));
        assertFalse(MetalConfig.rollDrop(fixed(0.004), 0.4));
        assertTrue(MetalConfig.rollDrop(fixed(0.019), 2.0));
        assertFalse(MetalConfig.rollDrop(fixed(0.0), 0.0));
    }

    @Test
    void smeltBatchLimitedByRawMoneyAndCap() {
        MetalConfig config = shipped();
        assertEquals(2, config.smeltTimes(10, Long.MAX_VALUE, Tier.COMMON, 64));
        assertEquals(5, config.smeltTimes(400, 5_000, Tier.COMMON, 64));
        assertEquals(1, config.smeltTimes(400, 1_000_000, Tier.COMMON, 1));
        assertEquals(64, config.smeltTimes(1_000, Long.MAX_VALUE, Tier.COMMON, 999));
        assertEquals(0, config.smeltTimes(3, Long.MAX_VALUE, Tier.COMMON, 64));
        assertEquals(0, config.smeltTimes(40, 19_999, Tier.LEGENDARY, 64));
        assertEquals(9_000, config.smeltCost(Tier.RARE, 3));
        assertEquals(1_280_000, config.smeltCost(Tier.LEGENDARY, 64));
    }
}
