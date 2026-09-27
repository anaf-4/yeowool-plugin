package com.yeowool.life.treasure;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and reads treasure map items. Everything the hint needs (id, tier,
 * target x/z, expiry) is stored in the item's PDC so the per-second action
 * bar never touches the database.
 */
public final class TreasureMapItem {

    public record MapData(long id, TreasureTier tier, int x, int z, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Map<TreasureTier, String> itemsAdderIds;
    private final NamespacedKey idKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey xKey;
    private final NamespacedKey zKey;
    private final NamespacedKey expiresKey;

    public TreasureMapItem(JavaPlugin plugin, Map<TreasureTier, String> itemsAdderIds) {
        this.itemsAdderIds = itemsAdderIds;
        this.idKey = new NamespacedKey(plugin, "treasure_map_id");
        this.tierKey = new NamespacedKey(plugin, "treasure_map_tier");
        this.xKey = new NamespacedKey(plugin, "treasure_map_x");
        this.zKey = new NamespacedKey(plugin, "treasure_map_z");
        this.expiresKey = new NamespacedKey(plugin, "treasure_map_expires");
    }

    public ItemStack create(MapData data) {
        ItemStack stack = baseItem(data.tier());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(data.tier().label() + " 보물지도", data.tier().color()));
        meta.lore(List.of(
                line("야생 X " + TreasureRules.band(data.x()) + ", Z " + TreasureRules.band(data.z()) + " 부근", NamedTextColor.GRAY),
                line("야생 서버에서 손에 들면 방향과 거리를 알려줍니다", NamedTextColor.GRAY),
                line("보물 위치에서 웅크리고 땅을 우클릭하면 발굴", NamedTextColor.YELLOW),
                line("만료: " + DATE.format(Instant.ofEpochMilli(data.expiresAt())), NamedTextColor.DARK_GRAY),
                line("지도 #" + data.id(), NamedTextColor.DARK_GRAY)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.LONG, data.id());
        pdc.set(tierKey, PersistentDataType.STRING, data.tier().name());
        pdc.set(xKey, PersistentDataType.INTEGER, data.x());
        pdc.set(zKey, PersistentDataType.INTEGER, data.z());
        pdc.set(expiresKey, PersistentDataType.LONG, data.expiresAt());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<MapData> read(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        Long id = pdc.get(idKey, PersistentDataType.LONG);
        String tierName = pdc.get(tierKey, PersistentDataType.STRING);
        Integer x = pdc.get(xKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(zKey, PersistentDataType.INTEGER);
        Long expiresAt = pdc.get(expiresKey, PersistentDataType.LONG);
        if (id == null || tierName == null || x == null || z == null || expiresAt == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MapData(id, TreasureTier.valueOf(tierName), x, z, expiresAt));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The configured ItemsAdder item for this tier when available, otherwise plain paper. */
    private ItemStack baseItem(TreasureTier tier) {
        String id = itemsAdderIds.getOrDefault(tier, "");
        if (id != null && !id.isBlank() && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(id);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.PAPER);
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
