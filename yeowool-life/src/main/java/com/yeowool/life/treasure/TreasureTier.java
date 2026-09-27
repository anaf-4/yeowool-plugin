package com.yeowool.life.treasure;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Optional;

public enum TreasureTier {
    COMMON("common", "일반", NamedTextColor.WHITE),
    RARE("rare", "희귀", NamedTextColor.AQUA),
    LEGENDARY("legendary", "전설", NamedTextColor.GOLD);

    private final String configKey;
    private final String label;
    private final NamedTextColor color;

    TreasureTier(String configKey, String label, NamedTextColor color) {
        this.configKey = configKey;
        this.label = label;
        this.color = color;
    }

    public String configKey() {
        return configKey;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    /** Accepts the config key ({@code rare}), the Korean label ({@code 희귀}) or the enum name, ignoring case. */
    public static Optional<TreasureTier> parse(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (TreasureTier tier : values()) {
            if (tier.configKey.equals(normalized) || tier.label.equals(input.trim()) || tier.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
