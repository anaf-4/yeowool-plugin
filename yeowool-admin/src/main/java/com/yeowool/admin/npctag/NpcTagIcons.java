package com.yeowool.admin.npctag;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * The "scottgb_npc_tags" pack ({@code npc_tags} namespace) — 5 tag types
 * (craft/info/open/shop/talk) × 5 colors, imported as ItemsAdder font_images
 * the same way {@code beautiful_ranks}' rank icons already are
 * ({@code scale_ratio: 8}/{@code y_position: 8}, the proven "inline glyph in
 * text" convention — not the GUI-background trick used elsewhere, so no
 * offset tuning needed here).
 */
public final class NpcTagIcons {

    public static final List<String> TYPES = List.of("craft", "info", "open", "shop", "talk");
    public static final List<String> COLORS = List.of("black", "blue", "green", "purple", "red");

    private NpcTagIcons() {
    }

    /** Null if ItemsAdder/the pack isn't available or {@code type}/{@code color} don't resolve to a real glyph. */
    public static Component glyph(String type, String color) {
        String legacy = resolveRaw(type, color);
        return legacy == null ? null : LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    /** The raw resolved legacy string (§-codes and the private-use glyph character) — for embedding directly into a dispatched command's text, rather than a Component. */
    public static String glyphChar(String type, String color) {
        return resolveRaw(type, color);
    }

    private static String resolveRaw(String type, String color) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        String id = type + "_" + color;
        String placeholder = ":" + id + ":";
        String legacy = FontImageWrapper.replaceFontImages(placeholder);
        if (legacy.contains(placeholder)) {
            return null;
        }
        return legacy;
    }
}
