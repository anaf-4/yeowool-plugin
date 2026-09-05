package com.yeowool.enhance;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * PDC-backed enhance state on the item itself (survives inventory moves,
 * chests, trades — same rationale as {@code ItemFlags} in YeowoolAdmin). The
 * item's original display name/lore are captured once (first ever enhance)
 * so every later {@link #applyLevel} rebuild restores them exactly and just
 * layers the "+N강" prefix / enhance lore block on top, instead of
 * accumulating stale text. An empty captured name means "no custom name" —
 * rebuilt as {@link Component#translatable} so the vanilla client-side
 * translation (Korean included) still applies to the base item name.
 *
 * <p>Also drives the vanilla 1.21.2+ {@code minecraft:tooltip_style} item
 * component (Ultimate Tooltips resourcepack, {@code assets/minecraft/textures/
 * gui/sprites/tooltip/<style>_{background,frame}.png}, merged in as the
 * {@code yeowool_tooltips} ItemsAdder content pack): the item's hover tooltip
 * box itself gets a themed border/background matching its current
 * {@link EnhanceTier}, escalating through {@link #TOOLTIP_STYLES} by that
 * tier's ordinal position in {@link EnhanceConfig#tiers()} — so any tier an
 * admin adds to config.yml automatically gets the next style in line, up to
 * "artifact" for the 5th tier and beyond.
 */
public final class EnhanceItemData {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Key[] TOOLTIP_STYLES = {
            Key.key("minecraft", "common"),
            Key.key("minecraft", "rare"),
            Key.key("minecraft", "epic"),
            Key.key("minecraft", "legendary"),
            Key.key("minecraft", "artifact"),
    };

    private final NamespacedKey levelKey;
    private final NamespacedKey baseNameKey;
    private final NamespacedKey baseLoreKey;
    private final NamespacedKey attackDamageModifierKey;
    private final NamespacedKey armorModifierKey;
    private final EnhanceConfig config;

    public EnhanceItemData(JavaPlugin plugin, EnhanceConfig config) {
        this.levelKey = new NamespacedKey(plugin, "enhance_level");
        this.baseNameKey = new NamespacedKey(plugin, "enhance_base_name");
        this.baseLoreKey = new NamespacedKey(plugin, "enhance_base_lore");
        this.attackDamageModifierKey = new NamespacedKey(plugin, "enhance_attack_damage");
        this.armorModifierKey = new NamespacedKey(plugin, "enhance_armor");
        this.config = config;
    }

    public boolean isEnhanceable(ItemStack item) {
        return item != null && !item.getType().isAir() && MaterialCategory.of(item.getType()) != MaterialCategory.NONE;
    }

    public int level(ItemStack item) {
        if (item == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    /** Sets the enhance level and fully rebuilds display name / lore / attribute modifiers from the captured base state. */
    public void applyLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        captureBaseIfAbsent(item, meta);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, level);

        EnhanceTier tier = config.tierFor(level);
        String baseNameRaw = pdc.getOrDefault(baseNameKey, PersistentDataType.STRING, "");
        Component baseName = baseNameRaw.isEmpty()
                ? Component.translatable(item.getType().getItemTranslationKey())
                : MINI_MESSAGE.deserialize(baseNameRaw);

        if (level > 0) {
            meta.displayName(Component.text("+" + level + "강 ", tier.color())
                    .append(baseName)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (!baseNameRaw.isEmpty()) {
            meta.displayName(baseName.decoration(TextDecoration.ITALIC, false));
        } else {
            meta.displayName(null);
        }

        List<Component> lore = new ArrayList<>();
        String baseLoreRaw = pdc.getOrDefault(baseLoreKey, PersistentDataType.STRING, "");
        if (!baseLoreRaw.isEmpty()) {
            for (String line : baseLoreRaw.split("\n", -1)) {
                lore.add(MINI_MESSAGE.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
        }

        MaterialCategory category = MaterialCategory.of(item.getType());
        if (level > 0) {
            lore.add(Component.text("등급: ", NamedTextColor.GRAY).append(Component.text(tier.name(), tier.color()))
                    .decoration(TextDecoration.ITALIC, false));
            if (category == MaterialCategory.WEAPON) {
                lore.add(Component.text(String.format("공격력 +%.1f", weaponBonus(level, tier)), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (category == MaterialCategory.ARMOR) {
                lore.add(Component.text(String.format("방어력 +%.1f", armorBonus(level, tier)), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore.isEmpty() ? null : lore);

        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ARMOR);
        if (level > 0 && category == MaterialCategory.WEAPON) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    attackDamageModifierKey, weaponBonus(level, tier), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        } else if (level > 0 && category == MaterialCategory.ARMOR) {
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                    armorModifierKey, armorBonus(level, tier), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR));
        }

        item.setItemMeta(meta);

        if (level > 0) {
            item.setData(DataComponentTypes.TOOLTIP_STYLE, tooltipStyleFor(tier));
        } else {
            item.unsetData(DataComponentTypes.TOOLTIP_STYLE);
        }
    }

    private Key tooltipStyleFor(EnhanceTier tier) {
        int index = config.tiers().indexOf(tier);
        if (index < 0) {
            index = 0;
        }
        return TOOLTIP_STYLES[Math.min(index, TOOLTIP_STYLES.length - 1)];
    }

    private double weaponBonus(int level, EnhanceTier tier) {
        return level * config.weaponAttackDamagePerLevel() * tier.statMultiplier();
    }

    private double armorBonus(int level, EnhanceTier tier) {
        return level * config.armorArmorPerLevel() * tier.statMultiplier();
    }

    private void captureBaseIfAbsent(ItemStack item, ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(baseNameKey, PersistentDataType.STRING)) {
            return;
        }
        pdc.set(baseNameKey, PersistentDataType.STRING,
                meta.hasDisplayName() && meta.displayName() != null ? MINI_MESSAGE.serialize(meta.displayName()) : "");
        if (meta.hasLore() && meta.lore() != null) {
            List<String> lines = new ArrayList<>();
            for (Component line : meta.lore()) {
                lines.add(MINI_MESSAGE.serialize(line));
            }
            pdc.set(baseLoreKey, PersistentDataType.STRING, String.join("\n", lines));
        } else {
            pdc.set(baseLoreKey, PersistentDataType.STRING, "");
        }
    }
}
