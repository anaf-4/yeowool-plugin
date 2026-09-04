package com.yeowool.admin.itemtool;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PDC-backed metadata an OP can attach to any held item via {@code /귀속}
 * and {@code /기간제}: whether it's bound (soulbound) and, if timed, its
 * duration plus the actual expiry timestamp (set lazily — see
 * {@link TimedItemTask} — the first time a non-op player ends up holding it,
 * per "기간제는 일반 유저가 아이템을 들면 그걸 기준으로 시간이 지나도록").
 * Living on the {@link ItemStack} itself means the flags travel with it
 * through any inventory, chest, or trade.
 *
 * <p>A timed item also gets a {@code 7일제}-style tag appended to its lore
 * (see {@link #refreshTimedTag}) so players can see the limit at a glance.
 * That tag is re-applied every time it could otherwise be lost — after
 * {@code /기간제 설정}/{@code /기간제 해제} and after {@code /아이템설명 변경}
 * — so editing the description never silently removes it.
 */
public final class ItemFlags {

    // "@?" keeps matching the old "@N일제" format too, so refreshing an item made before the "@" was dropped replaces it cleanly instead of leaving both.
    private static final Pattern TAG_LINE = Pattern.compile("^@?\\d+(일|시간|분|초)제$");

    private final NamespacedKey boundKey;
    private final NamespacedKey timedDurationKey;
    private final NamespacedKey timedExpiresAtKey;

    public ItemFlags(JavaPlugin plugin) {
        this.boundKey = new NamespacedKey(plugin, "bound");
        this.timedDurationKey = new NamespacedKey(plugin, "timed_duration_ms");
        this.timedExpiresAtKey = new NamespacedKey(plugin, "timed_expires_at");
    }

    public boolean isBound(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(boundKey, PersistentDataType.BYTE);
    }

    public void setBound(ItemStack item, boolean bound) {
        ItemMeta meta = item.getItemMeta();
        if (bound) {
            meta.getPersistentDataContainer().set(boundKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().remove(boundKey);
        }
        item.setItemMeta(meta);
    }

    public boolean isTimed(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(timedDurationKey, PersistentDataType.LONG);
    }

    public long getTimedDurationMs(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Long value = meta == null ? null : meta.getPersistentDataContainer().get(timedDurationKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public void setTimedDurationMs(ItemStack item, long durationMs) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(timedDurationKey, PersistentDataType.LONG, durationMs);
        meta.getPersistentDataContainer().remove(timedExpiresAtKey); // clears any previously-started countdown
        item.setItemMeta(meta);
        refreshTimedTag(item);
    }

    public void clearTimed(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(timedDurationKey);
        meta.getPersistentDataContainer().remove(timedExpiresAtKey);
        item.setItemMeta(meta);
        refreshTimedTag(item);
    }

    public boolean hasStartedCountdown(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(timedExpiresAtKey, PersistentDataType.LONG);
    }

    public void startCountdown(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        long durationMs = getTimedDurationMs(item);
        meta.getPersistentDataContainer().set(timedExpiresAtKey, PersistentDataType.LONG, System.currentTimeMillis() + durationMs);
        item.setItemMeta(meta);
    }

    public boolean isExpired(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Long expiresAt = meta == null ? null : meta.getPersistentDataContainer().get(timedExpiresAtKey, PersistentDataType.LONG);
        return expiresAt != null && System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Strips any previous {@code N일제}-style line from the lore and, if
     * the item is currently timed, appends a fresh one reflecting its
     * configured duration. Call this after anything that could have wiped
     * the lore (setting/clearing the timer, or {@code /아이템설명 변경}).
     */
    public void refreshTimedTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(line -> TAG_LINE.matcher(PlainTextComponentSerializer.plainText().serialize(line)).matches());

        if (isTimed(item)) {
            lore.add(buildTagLine(getTimedDurationMs(item)));
        }

        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    private Component buildTagLine(long durationMs) {
        return Component.text(formatDuration(durationMs), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    private String formatDuration(long ms) {
        if (ms % 86_400_000L == 0 && ms / 86_400_000L > 0) {
            return (ms / 86_400_000L) + "일제";
        }
        if (ms % 3_600_000L == 0 && ms / 3_600_000L > 0) {
            return (ms / 3_600_000L) + "시간제";
        }
        if (ms % 60_000L == 0 && ms / 60_000L > 0) {
            return (ms / 60_000L) + "분제";
        }
        return Math.max(1L, ms / 1000L) + "초제";
    }
}
