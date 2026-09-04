package com.yeowool.enchant;

import net.kyori.adventure.text.format.NamedTextColor;

/** {@code /인챈트강화}'s 6 book rarities — a completely separate system from {@code /강화}'s +N강. */
public enum EnchantTier {
    SIMPLE("심플", NamedTextColor.WHITE, "yeowool_enhance:book_simple"),
    UNIQUE("유니크", NamedTextColor.GREEN, "yeowool_enhance:book_unique"),
    ELITE("엘리트", NamedTextColor.AQUA, "yeowool_enhance:book_elite"),
    ULTIMATE("얼티밋", NamedTextColor.YELLOW, "yeowool_enhance:book_ultimate"),
    LEGENDARY("레전더리", NamedTextColor.GOLD, "yeowool_enhance:book_legendary"),
    FABLED("페이블드", NamedTextColor.LIGHT_PURPLE, "yeowool_enhance:book_fabled");

    private final String displayName;
    private final NamedTextColor color;
    private final String bookIconId;

    EnchantTier(String displayName, NamedTextColor color, String bookIconId) {
        this.displayName = displayName;
        this.color = color;
        this.bookIconId = bookIconId;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    /** ItemsAdder skin (a real {@code Material.ENCHANTED_BOOK} with a custom model) — see {@link EnchantItems#createBook}. */
    public String bookIconId() {
        return bookIconId;
    }

    /** Null if this is already {@link #FABLED}. */
    public EnchantTier next() {
        int ordinal = ordinal();
        EnchantTier[] values = values();
        return ordinal + 1 < values.length ? values[ordinal + 1] : null;
    }
}
