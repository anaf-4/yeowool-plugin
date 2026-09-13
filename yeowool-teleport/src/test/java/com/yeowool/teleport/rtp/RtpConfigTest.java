package com.yeowool.teleport.rtp;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link RtpConfig#load}'s parsing and {@link RtpConfig#radiusFor} —
 * a bad radius or cooldown here either strands players in unsafe far-out
 * terrain or lets {@code /rtp} bypass its own cooldown entirely.
 */
class RtpConfigTest {

    private static final String YAML = """
            rtp:
              enabled: false
              cooldown-seconds: 120
              min-radius: 50
              radius:
                overworld: 3000
                nether: 500
                end: 1500
              gui-background-offset: -4
            """;

    @Test
    void loadReadsEveryFieldFromConfig() {
        RtpConfig config = RtpConfig.load(YamlConfiguration.loadConfiguration(new StringReader(YAML)));

        assertFalse(config.enabled());
        assertEquals(120, config.cooldownSeconds());
        assertEquals(50, config.minRadius());
        assertEquals(3000, config.overworldRadius());
        assertEquals(500, config.netherRadius());
        assertEquals(1500, config.endRadius());
        assertEquals(-4, config.backgroundOffsetPx());
    }

    @Test
    void loadFallsBackToDefaultsWhenRtpSectionIsMissing() {
        RtpConfig config = RtpConfig.load(new YamlConfiguration());

        assertTrue(config.enabled());
        assertEquals(300, config.cooldownSeconds());
        assertEquals(100, config.minRadius());
        assertEquals(5000, config.overworldRadius());
        assertEquals(1000, config.netherRadius());
        assertEquals(2000, config.endRadius());
    }

    @Test
    void radiusForPicksTheRadiusMatchingTheDimension() {
        RtpConfig config = RtpConfig.load(YamlConfiguration.loadConfiguration(new StringReader(YAML)));

        assertEquals(3000, config.radiusFor(World.Environment.NORMAL));
        assertEquals(500, config.radiusFor(World.Environment.NETHER));
        assertEquals(1500, config.radiusFor(World.Environment.THE_END));
        assertEquals(3000, config.radiusFor(World.Environment.CUSTOM), "unrecognized dimensions fall back to the overworld radius");
    }
}
