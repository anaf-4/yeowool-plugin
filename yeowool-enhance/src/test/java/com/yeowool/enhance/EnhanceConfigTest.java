package com.yeowool.enhance;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link EnhanceConfig}'s pure tier/rate lookups — the entire balance
 * model for the real-money-adjacent enhance gamble. Loaded from a YAML
 * fixture mirroring the shipped config.yml (not a running server) since
 * {@link EnhanceConfig#load} only ever reads a {@code FileConfiguration}.
 */
class EnhanceConfigTest {

    private static final String YAML = """
            enhance:
              max-level: 30
              tiers:
                - level: 0
                  name: "일반"
                  color: WHITE
                  stat-multiplier: 1.0
                - level: 10
                  name: "희귀"
                  color: AQUA
                  stat-multiplier: 1.3
                - level: 15
                  name: "에픽"
                  color: LIGHT_PURPLE
                  stat-multiplier: 1.7
                - level: 20
                  name: "전설"
                  color: GOLD
                  stat-multiplier: 2.2
                - level: 25
                  name: "신화"
                  color: RED
                  stat-multiplier: 3.0
              success-rate:
                default: 90.0
                per-level:
                  9: 70.0
                  14: 50.0
                  24: 15.0
              fail-destroy-start-level: 15
              fail-destroy-chance-percent: 15.0
              fail-downgrade-chance-percent: 35.0
              protection-item: NETHERITE_INGOT
              weapon-attack-damage-per-level: 0.4
              armor-armor-per-level: 0.25
            """;

    private EnhanceConfig config() {
        return EnhanceConfig.load(YamlConfiguration.loadConfiguration(new StringReader(YAML)));
    }

    @Test
    void tierForReturnsHighestThresholdAtOrBelowLevel() {
        EnhanceConfig config = config();
        assertEquals("일반", config.tierFor(0).name());
        assertEquals("일반", config.tierFor(9).name());
        assertEquals("희귀", config.tierFor(10).name());
        assertEquals("희귀", config.tierFor(14).name());
        assertEquals("에픽", config.tierFor(15).name());
        assertEquals("신화", config.tierFor(30).name());
    }

    @Test
    void crossesTierAtOnlyTrueRightBeforeAThreshold() {
        EnhanceConfig config = config();
        assertTrue(config.crossesTierAt(9), "9 -> 10 enters 희귀");
        assertTrue(config.crossesTierAt(14), "14 -> 15 enters 에픽");
        assertFalse(config.crossesTierAt(10), "10 -> 11 stays 희귀");
        assertFalse(config.crossesTierAt(0), "0 -> 1 stays 일반");
    }

    @Test
    void successRateUsesOverrideWhenPresentOtherwiseDefault() {
        EnhanceConfig config = config();
        assertEquals(70.0, config.successRate(9));
        assertEquals(50.0, config.successRate(14));
        assertEquals(15.0, config.successRate(24));
        assertEquals(90.0, config.successRate(0));
        assertEquals(90.0, config.successRate(20));
    }

    @Test
    void isFailRiskyOnlyAtOrAboveDestroyStartLevel() {
        EnhanceConfig config = config();
        assertFalse(config.isFailRisky(14));
        assertTrue(config.isFailRisky(15));
        assertTrue(config.isFailRisky(29));
    }

    @Test
    void transcendStagesParseAndMissingStageIsEmpty() {
        String yaml = YAML + """
                  tool-mining-efficiency-per-level: 0.5
                  transcend:
                    protect-from-destroy: false
                    stages:
                      - stage: 1
                        name: "1차 초월"
                        color: DARK_AQUA
                        stat-multiplier: 3.2
                        stone-item: "yeowool_enhance:transcend_stone_1"
                        stone-amount: 1
                        currency: 500000
                        success-rate: 60.0
                        enhance-cost-multiplier: 2.0
                """;
        EnhanceConfig config = EnhanceConfig.load(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
        TranscendStage first = config.transcendStage(1).orElseThrow();
        assertEquals("1차 초월", first.name());
        assertEquals(3.2, first.statMultiplier());
        assertEquals("yeowool_enhance:transcend_stone_1", first.stoneItemId());
        assertEquals(500000L, first.currency());
        assertEquals(60.0, first.successRate());
        assertEquals(2.0, first.enhanceCostMultiplier());
        assertTrue(config.transcendStage(2).isEmpty());
        assertFalse(config.transcendProtectFromDestroy());
        assertEquals(0.5, config.toolMiningEfficiencyPerLevel());
        assertEquals(0.008, config.armorToughnessPerLevel());
        assertEquals(0.04, config.armorHealthPerLevel());
    }
}
