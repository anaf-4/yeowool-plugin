package com.yeowool.enhance;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
import java.util.Optional;
import java.util.Set;

/**
 * PDC-backed enhance state on the item itself (survives inventory moves,
 * chests, trades — same rationale as {@code ItemFlags} in YeowoolAdmin): the
 * enhance level and the transcend stage (0 = not transcended). The item's
 * original display name/lore are captured once (first ever enhance) so every
 * rebuild restores them exactly and just layers the "+N강 [초월]" prefix /
 * enhance lore block on top. An empty captured name means "no custom name" —
 * rebuilt as {@link Component#translatable} so the vanilla client-side
 * translation (Korean included) still applies, including after the first
 * transcendence turns the item into its netherite version.
 *
 * <p>Attributes: since 1.21 an item that carries any attribute modifier no
 * longer gets its material's default ones (base attack damage/speed, armor),
 * so every rebuild re-adds the material defaults, keeps modifiers other
 * plugins added (non-minecraft namespaces), then adds the enhance bonus.
 *
 * <p>Also drives the vanilla 1.21.2+ {@code minecraft:tooltip_style} item
 * component (Ultimate Tooltips resourcepack, merged in as the
 * {@code yeowool_tooltips} ItemsAdder content pack): the tooltip frame
 * escalates by tier position in {@link EnhanceConfig#tiers()}, and any
 * transcended item uses the top style.
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
    private final NamespacedKey stageKey;
    private final NamespacedKey baseNameKey;
    private final NamespacedKey baseLoreKey;
    private final NamespacedKey attackDamageModifierKey;
    private final NamespacedKey armorModifierKey;
    private final NamespacedKey miningModifierKey;
    private final Set<NamespacedKey> ownModifierKeys;
    private final EnhanceConfig config;

    public EnhanceItemData(JavaPlugin plugin, EnhanceConfig config) {
        this.levelKey = new NamespacedKey(plugin, "enhance_level");
        this.stageKey = new NamespacedKey(plugin, "enhance_transcend");
        this.baseNameKey = new NamespacedKey(plugin, "enhance_base_name");
        this.baseLoreKey = new NamespacedKey(plugin, "enhance_base_lore");
        this.attackDamageModifierKey = new NamespacedKey(plugin, "enhance_attack_damage");
        this.armorModifierKey = new NamespacedKey(plugin, "enhance_armor");
        this.miningModifierKey = new NamespacedKey(plugin, "enhance_mining");
        this.ownModifierKeys = Set.of(attackDamageModifierKey, armorModifierKey, miningModifierKey);
        this.config = config;
    }

    public boolean isEnhanceable(ItemStack item) {
        return item != null && !item.getType().isAir() && MaterialCategory.of(item.getType()) != MaterialCategory.NONE;
    }

    public int level(ItemStack item) {
        return readInt(item, levelKey);
    }

    /** 0 = not transcended, 1..3 = transcend stage. */
    public int stage(ItemStack item) {
        return readInt(item, stageKey);
    }

    private int readInt(ItemStack item, NamespacedKey key) {
        if (item == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    /** Sets the enhance level, keeping the current transcend stage. */
    public void applyLevel(ItemStack item, int level) {
        applyState(item, stage(item), level);
    }

    /** Sets stage + level and fully rebuilds display name / lore / attribute modifiers from the captured base state. */
    public void applyState(ItemStack item, int stage, int level) {
        ItemMeta meta = item.getItemMeta();
        captureBaseIfAbsent(meta);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(stageKey, PersistentDataType.INTEGER, stage);

        EnhanceTier tier = config.tierFor(level);
        Optional<TranscendStage> transcend = stage > 0 ? config.transcendStage(stage) : Optional.empty();
        String gradeName = transcend.map(TranscendStage::name).orElse(stage > 0 ? stage + "차 초월" : tier.name());
        NamedTextColor gradeColor = transcend.map(TranscendStage::color).orElse(stage > 0 ? NamedTextColor.DARK_PURPLE : tier.color());
        double stageMultiplier = transcend.map(TranscendStage::statMultiplier)
                .orElse(config.tierFor(config.maxLevel()).statMultiplier());
        boolean enhanced = level > 0 || stage > 0;

        String baseNameRaw = pdc.getOrDefault(baseNameKey, PersistentDataType.STRING, "");
        Component baseName = baseNameRaw.isEmpty()
                ? Component.translatable(item.getType().getItemTranslationKey())
                : MINI_MESSAGE.deserialize(baseNameRaw);

        if (enhanced) {
            Component prefix = Component.text("+" + level + "강 ", gradeColor);
            if (stage > 0) {
                prefix = prefix.append(Component.text("[" + gradeName + "] ", gradeColor));
            }
            meta.displayName(prefix.append(baseName).decoration(TextDecoration.ITALIC, false));
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
        double bonus = TranscendRules.bonus(stage, level, config.maxLevel(), perLevel(category), tier.statMultiplier(), stageMultiplier);
        if (enhanced) {
            lore.add(Component.text("등급: ", NamedTextColor.GRAY).append(Component.text(gradeName, gradeColor))
                    .decoration(TextDecoration.ITALIC, false));
            switch (category) {
                case WEAPON -> lore.add(Component.text(String.format("공격력 +%.1f", bonus), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                case ARMOR -> lore.add(Component.text(String.format("방어력 +%.1f", bonus), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                case TOOL -> lore.add(Component.text(String.format("채굴 속도 +%.1f", bonus), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                default -> {
                }
            }
        }
        meta.lore(lore.isEmpty() ? null : lore);

        rebuildAttributes(item, meta, enhanced, category, bonus);
        item.setItemMeta(meta);

        if (enhanced) {
            item.setData(DataComponentTypes.TOOLTIP_STYLE, stage > 0 ? TOOLTIP_STYLES[TOOLTIP_STYLES.length - 1] : tooltipStyleFor(tier));
        } else {
            item.unsetData(DataComponentTypes.TOOLTIP_STYLE);
        }
    }

    private double perLevel(MaterialCategory category) {
        return switch (category) {
            case WEAPON -> config.weaponAttackDamagePerLevel();
            case ARMOR -> config.armorArmorPerLevel();
            case TOOL -> config.toolMiningEfficiencyPerLevel();
            default -> 0;
        };
    }

    /**
     * Keeps other plugins' modifiers, drops vanilla-namespace copies and our own, then — whenever the
     * item will carry any modifier — re-adds the material defaults first (otherwise 1.21 drops them).
     */
    private void rebuildAttributes(ItemStack item, ItemMeta meta, boolean enhanced, MaterialCategory category, double bonus) {
        Multimap<Attribute, AttributeModifier> kept = ArrayListMultimap.create();
        Multimap<Attribute, AttributeModifier> existing = meta.getAttributeModifiers();
        if (existing != null) {
            existing.forEach((attribute, modifier) -> {
                NamespacedKey key = modifier.getKey();
                if (!NamespacedKey.MINECRAFT.equals(key.getNamespace()) && !ownModifierKeys.contains(key)) {
                    kept.put(attribute, modifier);
                }
            });
        }
        meta.setAttributeModifiers(null);
        if (!enhanced && kept.isEmpty()) {
            return; // no modifiers at all → vanilla defaults apply by themselves
        }
        item.getType().getDefaultAttributeModifiers().forEach(meta::addAttributeModifier);
        kept.forEach(meta::addAttributeModifier);
        if (!enhanced || bonus <= 0) {
            return;
        }
        switch (category) {
            case WEAPON -> meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    attackDamageModifierKey, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            case ARMOR -> meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                    armorModifierKey, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR));
            case TOOL -> meta.addAttributeModifier(Attribute.MINING_EFFICIENCY, new AttributeModifier(
                    miningModifierKey, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            default -> {
            }
        }
    }

    private Key tooltipStyleFor(EnhanceTier tier) {
        int index = config.tiers().indexOf(tier);
        if (index < 0) {
            index = 0;
        }
        return TOOLTIP_STYLES[Math.min(index, TOOLTIP_STYLES.length - 1)];
    }

    private void captureBaseIfAbsent(ItemMeta meta) {
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
