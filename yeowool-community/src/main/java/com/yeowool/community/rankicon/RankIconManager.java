package com.yeowool.community.rankicon;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rank icons are admin-assigned (not purchased/unlocked), so unlike
 * {@code TitleManager}/{@code CosmeticManager} there is no "unlocked ids"
 * set — just a single equipped id per player, stored the same way via
 * {@link PlayerData#setSetting}. Players with nothing explicitly assigned
 * fall back to {@link #resolveIconId}'s configured default (e.g.
 * {@code player_icon}) rather than showing no icon at all. Rendering goes
 * through ItemsAdder's {@code font_images} feature via the {@code :id:}
 * chat-placeholder substitution API ({@link FontImageWrapper#replaceFontImages(String)}),
 * NOT by constructing {@link FontImageWrapper} directly — that constructor
 * throws {@link IllegalArgumentException} for an unknown id instead of
 * leaving {@code exists()} to report it safely, and (separately) its
 * {@code getString()} output didn't actually render in chat/tab-list/
 * scoreboard the way {@code replaceFontImages}'s output does, so the
 * placeholder form is both the safe one and the one that's confirmed to
 * work end-to-end.
 */
public final class RankIconManager {

    private static final String EQUIPPED_SETTING = "rankicon.equipped";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private List<RankIconDefinition> definitions = List.of();
    private String defaultIconId = "";

    public RankIconManager(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        reload();
    }

    public void reload() {
        List<RankIconDefinition> parsed = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("rank-icons")) {
            try {
                String id = entry.get("id").toString();
                String display = entry.get("display").toString();
                parsed.add(new RankIconDefinition(id, display));
            } catch (Exception e) {
                plugin.getLogger().warning("rank-icons 설정 항목이 잘못되었습니다: " + entry);
            }
        }
        this.definitions = List.copyOf(parsed);
        this.defaultIconId = plugin.getConfig().getString("rank-icon-default-id", "");
    }

    public List<RankIconDefinition> all() {
        return definitions;
    }

    public Optional<RankIconDefinition> find(String id) {
        return definitions.stream().filter(i -> i.id().equalsIgnoreCase(id)).findFirst();
    }

    public Optional<String> equippedId(PlayerData data) {
        String id = data.getSetting(EQUIPPED_SETTING, "");
        return id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    /** {@link #equippedId} if the player has one explicitly assigned, otherwise the configured default (e.g. {@code player_icon}). */
    public String resolveIconId(PlayerData data) {
        return equippedId(data).orElse(defaultIconId);
    }

    public String defaultIconId() {
        return defaultIconId;
    }

    /** {@code data} works for an offline target too — see {@link com.yeowool.core.util.PlayerDataResolver}. */
    public boolean assign(PlayerData data, String iconId) {
        if (find(iconId).isEmpty()) {
            return false;
        }
        data.setSetting(EQUIPPED_SETTING, iconId);
        return true;
    }

    /** {@code data} works for an offline target too — see {@link com.yeowool.core.util.PlayerDataResolver}. */
    public void clear(PlayerData data) {
        data.setSetting(EQUIPPED_SETTING, "");
    }

    /**
     * Renders {@code iconId}, or an empty component when ItemsAdder isn't
     * installed or the pack has no such image (uninstalled/renamed pack,
     * `/iareload`+`/iazip` not run yet) — callers just append this in front
     * of the name either way. Checks {@code .contains(placeholder)} rather
     * than {@code .equals(placeholder)} to detect a failed substitution:
     * an unmatched placeholder isn't guaranteed to come back completely
     * untouched (e.g. wrapped in an incidental reset code), so an exact
     * equality check let a failed substitution's literal {@code :id:} text
     * leak through as if it had rendered successfully.
     */
    public Component renderIcon(String iconId) {
        if (iconId == null || iconId.isBlank() || !isItemsAdderAvailable()) {
            return Component.empty();
        }
        String placeholder = ":" + iconId + ":";
        String legacy = FontImageWrapper.replaceFontImages(placeholder);
        if (legacy.contains(placeholder)) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private boolean isItemsAdderAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }
}
