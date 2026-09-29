# 강화 초월 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transcendence for enhanced gear (30강 → 1차 초월 to netherite and back to +0 → +10 2차 → +20 3차 → +30 max), tools become enhanceable, enhanced gear keeps its vanilla base stats, and world boss winners get transcendence stones.

**Architecture:** `yeowool-enhance` stores a transcend stage (0–3) in the item PDC next to the existing level. Pure `TranscendRules` (tested) decides level caps, when transcendence is allowed, the netherite material name, and the stat formula. `EnhanceItemData` rebuilds name/lore/attributes from (stage, level) and now re-adds the material's default attribute modifiers. `EnhanceService` gains `transcend`, the GUI swaps its action button at a gate, and `/초월석 지급` issues stones (ItemsAdder items) through the mailbox. `yeowool-raid`'s world boss runs configurable console commands per rank / participation.

**Tech Stack:** Paper 1.21.4, Java 21, ItemsAdder API (CustomStack), core Mailbox, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-30-enhance-transcend-design.md`

## Global Constraints

- Build from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`: `./gradlew :yeowool-enhance:build` (Task 4 `:yeowool-raid:build`, help `:yeowool-core:build`). OneDrive lock (`Unable to delete directory ...` / `Cannot snapshot ...output.bin`) → `rm -rf yeowool-*/build/test-results` and rerun.
- Bukkit/inventory/wallet/mailbox on the main thread (all enhance code already runs there).
- Player-visible text Korean via `MessageService` keys in `yeowool-enhance/src/main/resources/messages.yml` (MiniMessage; no raw `<word>` in literal text — use `[word]`); values via `Placeholder.unparsed`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: TranscendRules + tests

**Files:**
- Create: `yeowool-enhance/src/main/java/com/yeowool/enhance/TranscendRules.java`
- Test: `yeowool-enhance/src/test/java/com/yeowool/enhance/TranscendRulesTest.java`

**Interfaces:**
- Produces: `TranscendRules.MAX_STAGE` (3); `levelCap(int stage, int maxLevel) -> int`; `canTranscend(int stage, int level, int maxLevel) -> boolean`; `netheriteVariant(String materialName) -> String`; `bonus(int stage, int level, int maxLevel, double perLevel, double tierMultiplier, double stageMultiplier) -> double`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.enhance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscendRulesTest {

    @Test
    void levelCapPerStage() {
        assertEquals(30, TranscendRules.levelCap(0, 30));
        assertEquals(10, TranscendRules.levelCap(1, 30));
        assertEquals(20, TranscendRules.levelCap(2, 30));
        assertEquals(30, TranscendRules.levelCap(3, 30));
    }

    @Test
    void transcendOnlyAtEachGate() {
        assertTrue(TranscendRules.canTranscend(0, 30, 30));
        assertFalse(TranscendRules.canTranscend(0, 29, 30));
        assertTrue(TranscendRules.canTranscend(1, 10, 30));
        assertFalse(TranscendRules.canTranscend(1, 9, 30));
        assertTrue(TranscendRules.canTranscend(2, 20, 30));
        assertFalse(TranscendRules.canTranscend(3, 30, 30));
    }

    @Test
    void netheriteVariantOnlyForGearWithANetheriteVersion() {
        assertEquals("NETHERITE_SWORD", TranscendRules.netheriteVariant("DIAMOND_SWORD"));
        assertEquals("NETHERITE_PICKAXE", TranscendRules.netheriteVariant("WOODEN_PICKAXE"));
        assertEquals("NETHERITE_HELMET", TranscendRules.netheriteVariant("LEATHER_HELMET"));
        assertEquals("NETHERITE_BOOTS", TranscendRules.netheriteVariant("CHAINMAIL_BOOTS"));
        assertEquals("NETHERITE_HOE", TranscendRules.netheriteVariant("GOLDEN_HOE"));
        assertEquals("NETHERITE_AXE", TranscendRules.netheriteVariant("NETHERITE_AXE"));
        assertEquals("BOW", TranscendRules.netheriteVariant("BOW"));
        assertEquals("TURTLE_HELMET", TranscendRules.netheriteVariant("TURTLE_HELMET"));
        assertEquals("GOLDEN_APPLE", TranscendRules.netheriteVariant("GOLDEN_APPLE"));
    }

    @Test
    void bonusNeverDropsWhenTranscendingAndPeaksAtStageThree() {
        double beforeTranscend = TranscendRules.bonus(0, 30, 30, 0.4, 3.0, 0);
        double firstStageStart = TranscendRules.bonus(1, 0, 30, 0.4, 1.0, 3.2);
        double stageOneTop = TranscendRules.bonus(1, 10, 30, 0.4, 1.0, 3.2);
        double stageTwoStart = TranscendRules.bonus(2, 10, 30, 0.4, 1.0, 3.6);
        double max = TranscendRules.bonus(3, 30, 30, 0.4, 1.0, 4.2);
        assertEquals(36.0, beforeTranscend, 1e-9);
        assertEquals(38.4, firstStageStart, 1e-9);
        assertEquals(100.8, max, 1e-9);
        assertTrue(firstStageStart > beforeTranscend);
        assertTrue(stageTwoStart > stageOneTop);
    }
}
```

- [ ] **Step 2: Run** `./gradlew :yeowool-enhance:test --tests "com.yeowool.enhance.TranscendRulesTest"` → FAIL (class missing).

- [ ] **Step 3: Create `TranscendRules.java`**

```java
package com.yeowool.enhance;

import java.util.List;
import java.util.Set;

/**
 * Transcendence math, kept free of Bukkit so it can be unit tested. Stage 0 is "not transcended";
 * each stage caps how far enhancing can go until the next transcendence: 0 → max-level, 1 → 10,
 * 2 → 20, 3 → max-level (the final peak).
 */
public final class TranscendRules {

    public static final int MAX_STAGE = 3;

    private static final List<String> UPGRADABLE_PREFIXES = List.of(
            "WOODEN_", "STONE_", "IRON_", "GOLDEN_", "DIAMOND_", "LEATHER_", "CHAINMAIL_");
    private static final Set<String> NETHERITE_PIECES = Set.of(
            "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private TranscendRules() {
    }

    /** Highest enhance level reachable in {@code stage} before the next transcendence is required. */
    public static int levelCap(int stage, int maxLevel) {
        return switch (stage) {
            case 1 -> 10;
            case 2 -> 20;
            default -> maxLevel;
        };
    }

    /** True when the item sits at its stage's cap and another stage exists. */
    public static boolean canTranscend(int stage, int level, int maxLevel) {
        return stage < MAX_STAGE && level >= levelCap(stage, maxLevel);
    }

    /** The netherite material name for gear that has one (e.g. DIAMOND_SWORD → NETHERITE_SWORD), otherwise unchanged. */
    public static String netheriteVariant(String materialName) {
        for (String prefix : UPGRADABLE_PREFIXES) {
            if (materialName.startsWith(prefix)) {
                String piece = materialName.substring(prefix.length());
                if (NETHERITE_PIECES.contains(piece)) {
                    return "NETHERITE_" + piece;
                }
            }
        }
        return materialName;
    }

    /**
     * Before transcendence: {@code level × perLevel × tierMultiplier} (the original formula).
     * After: {@code (maxLevel + level) × perLevel × stageMultiplier} — the first transcendence keeps
     * the full pre-transcend levels as a base, so its multiplier just needs to exceed the top tier's.
     */
    public static double bonus(int stage, int level, int maxLevel, double perLevel, double tierMultiplier, double stageMultiplier) {
        if (stage <= 0) {
            return level * perLevel * tierMultiplier;
        }
        return (maxLevel + level) * perLevel * stageMultiplier;
    }
}
```

- [ ] **Step 4: Run** the same test → PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add yeowool-enhance/src/main/java/com/yeowool/enhance/TranscendRules.java yeowool-enhance/src/test/java/com/yeowool/enhance/TranscendRulesTest.java
git commit -m "Add enhance transcendence rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Config, tool category, item data (stage, display, base-stat fix)

**Files:**
- Create: `yeowool-enhance/src/main/java/com/yeowool/enhance/TranscendStage.java`
- Modify: `yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceConfig.java`
- Modify: `yeowool-enhance/src/main/java/com/yeowool/enhance/MaterialCategory.java`
- Replace: `yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceItemData.java`
- Modify: `yeowool-enhance/src/test/java/com/yeowool/enhance/EnhanceConfigTest.java`
- Modify: `yeowool-enhance/src/main/resources/config.yml`

**Interfaces:**
- Consumes: Task 1.
- Produces: `record TranscendStage(int stage, String name, NamedTextColor color, double statMultiplier, String stoneItemId, int stoneAmount, long currency, double successRate, double enhanceCostMultiplier)`; `EnhanceConfig.transcendStage(int) -> Optional<TranscendStage>`, `transcendProtectFromDestroy() -> boolean`, `toolMiningEfficiencyPerLevel() -> double`; `MaterialCategory.TOOL`; `EnhanceItemData.stage(ItemStack) -> int`, `applyState(ItemStack, int stage, int level)`, `applyLevel(ItemStack, int level)` (keeps stage).

- [ ] **Step 1: Create `TranscendStage.java`**

```java
package com.yeowool.enhance;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One transcendence step from config.yml: its display name/color, the stat multiplier used from then
 * on, what the attempt costs (stone item + 온) and succeeds with, and how much pricier normal enhancing
 * becomes at this stage.
 */
public record TranscendStage(int stage, String name, NamedTextColor color, double statMultiplier,
                             String stoneItemId, int stoneAmount, long currency, double successRate,
                             double enhanceCostMultiplier) {
}
```

- [ ] **Step 2: `EnhanceConfig.java`**
  - Add imports `java.util.Optional`.
  - Add fields `private final double toolMiningEfficiencyPerLevel;`, `private final List<TranscendStage> transcendStages;`, `private final boolean transcendProtectFromDestroy;` and matching constructor parameters appended after `armorArmorPerLevel` (assign them).
  - In `load`, before `return new EnhanceConfig(`, parse:

```java
        List<TranscendStage> transcendStages = new ArrayList<>();
        for (Map<?, ?> raw : root.getMapList("transcend.stages")) {
            try {
                NamedTextColor stageColor = NamedTextColor.NAMES.value(String.valueOf(raw.get("color")).toLowerCase());
                transcendStages.add(new TranscendStage(
                        ((Number) raw.get("stage")).intValue(),
                        String.valueOf(raw.get("name")),
                        stageColor == null ? NamedTextColor.DARK_PURPLE : stageColor,
                        ((Number) raw.get("stat-multiplier")).doubleValue(),
                        String.valueOf(raw.get("stone-item")),
                        raw.get("stone-amount") instanceof Number n ? n.intValue() : 1,
                        raw.get("currency") instanceof Number n ? n.longValue() : 0L,
                        raw.get("success-rate") instanceof Number n ? n.doubleValue() : 50.0,
                        raw.get("enhance-cost-multiplier") instanceof Number n ? n.doubleValue() : 1.0));
            } catch (RuntimeException e) {
                // skip a malformed stage entry
            }
        }
        transcendStages.sort(Comparator.comparingInt(TranscendStage::stage));
```

    and pass, after `root.getDouble("armor-armor-per-level", 0.25)`:

```java
                root.getDouble("tool-mining-efficiency-per-level", 0.3),
                transcendStages,
                root.getBoolean("transcend.protect-from-destroy", true)
```

  - Add getters:

```java
    public double toolMiningEfficiencyPerLevel() {
        return toolMiningEfficiencyPerLevel;
    }

    /** The configured stage {@code stage} (1..3), if any — a missing stage means transcendence stops before it. */
    public Optional<TranscendStage> transcendStage(int stage) {
        return transcendStages.stream().filter(s -> s.stage() == stage).findFirst();
    }

    /** Transcended gear is never destroyed by a failed enhance (the destroy roll becomes a downgrade). */
    public boolean transcendProtectFromDestroy() {
        return transcendProtectFromDestroy;
    }
```

- [ ] **Step 3: `MaterialCategory.java`** — add `TOOL` to the enum (`WEAPON, ARMOR, TOOL, NONE`) and, before `return NONE;`:

```java
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return TOOL;
        }
```

  Update the class javadoc to: `/** Weapons, armor and tools carry a meaningful "성능"(stat) bonus — everything else is never enhanceable. */`

- [ ] **Step 4: Replace `EnhanceItemData.java`** with:

```java
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
```

  If `NamespacedKey.MINECRAFT` doesn't exist as a constant, use the literal `"minecraft"`. If `getDefaultAttributeModifiers()` returns defaults whose keys collide when added twice, it's fine — we cleared everything first.

- [ ] **Step 5: `EnhanceConfigTest.java`** — add this test (and import `java.util.Optional` is not needed):

```java
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
    }
```

  (The YAML constant's lines are indented two spaces under `enhance:`; the appended block must use the same indentation so the keys land under `enhance`.)

- [ ] **Step 6: `config.yml`** — change the top comment line `# 강화 시스템 — 무기(검/도끼/활/석궁/삼지창)와 방어구(투구/흉갑/각반/신발)만 강화 가능합니다.` to `# 강화 시스템 — 무기(검/도끼/활/석궁/삼지창), 방어구(투구/흉갑/각반/신발), 도구(곡괭이/삽/괭이)를 강화할 수 있습니다.` and insert right after the `armor-armor-per-level: 0.25` line (still under `enhance:`):

```yaml
  # 도구(곡괭이/삽/괭이)는 강화 수치 * 아래 값 * 등급 배율만큼 채굴 속도가 오릅니다.
  tool-mining-efficiency-per-level: 0.3

  # 초월 — 30강 → 1차 초월(네더라이트로 변경, 0강부터 다시) → 10강에서 2차 초월 → 20강에서 3차 초월 → 30강이 최고 성능.
  # 초월한 뒤 능력치 = (30 + 강화 수치) * 1강당 보너스 * stat-multiplier (신화 등급 배율보다 커야 초월 직후 하락이 없습니다).
  # 초월 시도: stone-item(ItemsAdder 초월석) stone-amount개 + currency온, success-rate% — 실패해도 장비는 그대로(초월석·온만 소모).
  # 초월한 장비의 일반 강화 비용(온)은 단계별 비용 * enhance-cost-multiplier.
  transcend:
    # true면 초월한 장비는 강화 실패로 파괴되지 않고 하락만 합니다.
    protect-from-destroy: true
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
      - stage: 2
        name: "2차 초월"
        color: DARK_PURPLE
        stat-multiplier: 3.6
        stone-item: "yeowool_enhance:transcend_stone_2"
        stone-amount: 1
        currency: 1000000
        success-rate: 40.0
        enhance-cost-multiplier: 3.0
      - stage: 3
        name: "3차 초월"
        color: DARK_RED
        stat-multiplier: 4.2
        stone-item: "yeowool_enhance:transcend_stone_3"
        stone-amount: 1
        currency: 2000000
        success-rate: 25.0
        enhance-cost-multiplier: 4.0
```

- [ ] **Step 7: Build** — `./gradlew :yeowool-enhance:build` → BUILD SUCCESSFUL, tests pass.

- [ ] **Step 8: Commit**

```bash
git add yeowool-enhance/src/main/java/com/yeowool/enhance/TranscendStage.java yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceConfig.java yeowool-enhance/src/main/java/com/yeowool/enhance/MaterialCategory.java yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceItemData.java yeowool-enhance/src/test/java/com/yeowool/enhance/EnhanceConfigTest.java yeowool-enhance/src/main/resources/config.yml
git commit -m "Track transcend stage on items, enhance tools, keep vanilla base stats

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Service, GUI, /초월석, resources

**Files:**
- Replace: `yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceService.java`
- Modify: `yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceGui.java`
- Modify: `yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceMaterialResolver.java` (add `createItem`)
- Create: `yeowool-enhance/src/main/java/com/yeowool/enhance/TranscendStoneCommand.java`
- Modify: `yeowool-enhance/src/main/java/com/yeowool/enhance/YeowoolEnhance.java`
- Modify: `yeowool-enhance/src/main/resources/messages.yml`, `plugin.yml`
- Modify: `yeowool-core/src/main/resources/help.yml`

**Interfaces:**
- Consumes: Tasks 1–2; `core.mailbox().deliverOrStore(UUID, ItemStack, String, String)`; `Bukkit.getOfflinePlayerIfCached(String)`.
- Produces: `EnhanceService.attempt(Player, ItemStack)`, `transcend(Player) -> Result`, `isAtGate(ItemStack) -> boolean`, `enhanceCurrency(ItemStack) -> long`; `EnhanceMaterialResolver.createItem(String id, int amount) -> ItemStack` (null if unknown); `/초월석 지급 <닉네임> <1|2|3> [개수]`.

- [ ] **Step 1: Replace `EnhanceService.java`**

```java
package com.yeowool.enhance;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core enhance-attempt logic — deducts cost, rolls success, and on failure
 * (only once {@link EnhanceConfig#isFailRisky} for the current level) rolls
 * destroy vs. downgrade vs. plain fail, checking {@link EnhanceConfig#protectionItemId()}
 * first. Mutates the given {@link ItemStack} in place, so callers must pass
 * the actual live stack (e.g. from {@code getItemInMainHand()}), not a copy.
 * Cost (온/재료) comes from {@link EnhanceCostManager}, not {@link EnhanceConfig} —
 * that's the part {@code /강화설정} can change live, in-game; transcended gear
 * pays that 온 times its stage's {@code enhance-cost-multiplier}.
 *
 * <p>Transcendence ({@link #transcend}): at a stage's level cap the item can
 * move to the next stage for that stage's stone + 온; a failure only costs
 * those. The first transcendence turns the item into its netherite version
 * and restarts it at +0.
 */
public final class EnhanceService {

    public enum Result {
        SUCCESS, SUCCESS_TIER_UP, FAIL_SAFE, FAIL_PROTECTED, FAIL_DOWNGRADE, FAIL_DESTROYED,
        NOT_ENHANCEABLE, MAX_LEVEL, NEEDS_TRANSCEND, INSUFFICIENT_FUNDS, INSUFFICIENT_MATERIAL,
        TRANSCEND_SUCCESS, TRANSCEND_FAIL, NOT_AT_GATE, INSUFFICIENT_STONE
    }

    private final YeowoolCoreAPI core;
    private final EnhanceConfig config;
    private final EnhanceItemData itemData;
    private final EnhanceCostManager costs;

    public EnhanceService(YeowoolCoreAPI core, EnhanceConfig config, EnhanceItemData itemData, EnhanceCostManager costs) {
        this.core = core;
        this.config = config;
        this.itemData = itemData;
        this.costs = costs;
    }

    public EnhanceConfig config() {
        return config;
    }

    public EnhanceItemData itemData() {
        return itemData;
    }

    public EnhanceCostManager costs() {
        return costs;
    }

    /** True when the item sits at its stage's cap and the next stage is configured — the GUI shows "초월하기". */
    public boolean isAtGate(ItemStack item) {
        if (!itemData.isEnhanceable(item)) {
            return false;
        }
        int stage = itemData.stage(item);
        return TranscendRules.canTranscend(stage, itemData.level(item), config.maxLevel())
                && config.transcendStage(stage + 1).isPresent();
    }

    /** 온 for the next normal enhance of this item (per-level cost × the stage's multiplier). */
    public long enhanceCurrency(ItemStack item) {
        int stage = itemData.stage(item);
        double multiplier = stage > 0 ? config.transcendStage(stage).map(TranscendStage::enhanceCostMultiplier).orElse(1.0) : 1.0;
        return Math.round(costs.costFor(itemData.level(item)).currency() * multiplier);
    }

    public Result attempt(Player player, ItemStack item) {
        if (!itemData.isEnhanceable(item)) {
            return Result.NOT_ENHANCEABLE;
        }
        int stage = itemData.stage(item);
        int level = itemData.level(item);
        if (level >= TranscendRules.levelCap(stage, config.maxLevel())) {
            return isAtGate(item) ? Result.NEEDS_TRANSCEND : Result.MAX_LEVEL;
        }

        UUID uuid = player.getUniqueId();
        EnhanceCostManager.CostEntry cost = costs.costFor(level);
        long currency = enhanceCurrency(item);
        if (!core.economyData().hasBalance(uuid, currency)) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (!EnhanceMaterialResolver.hasAmount(player, cost.materialId(), cost.materialAmount())) {
            return Result.INSUFFICIENT_MATERIAL;
        }

        core.economyData().modifyBalance(uuid, -currency, "YeowoolEnhance",
                "강화 시도 (+" + level + " -> +" + (level + 1) + (stage > 0 ? ", " + stage + "차 초월" : "") + ")");
        EnhanceMaterialResolver.removeAmount(player, cost.materialId(), cost.materialAmount());

        boolean success = ThreadLocalRandom.current().nextDouble(100) < config.successRate(level);
        if (success) {
            boolean tierUp = stage == 0 && config.crossesTierAt(level);
            itemData.applyLevel(item, level + 1);
            return tierUp ? Result.SUCCESS_TIER_UP : Result.SUCCESS;
        }

        if (!config.isFailRisky(level)) {
            return Result.FAIL_SAFE;
        }
        double roll = ThreadLocalRandom.current().nextDouble(100);
        boolean destroy = roll < config.failDestroyChancePercent();
        boolean downgrade = !destroy && roll < config.failDestroyChancePercent() + config.failDowngradeChancePercent();
        if (destroy && stage > 0 && config.transcendProtectFromDestroy()) {
            destroy = false;
            downgrade = true;
        }
        if (!destroy && !downgrade) {
            return Result.FAIL_SAFE;
        }
        String protection = config.protectionItemId();
        if (protection != null && !protection.isBlank() && EnhanceMaterialResolver.hasAmount(player, protection, 1)) {
            EnhanceMaterialResolver.removeAmount(player, protection, 1);
            return Result.FAIL_PROTECTED;
        }
        if (destroy) {
            item.setAmount(0);
            return Result.FAIL_DESTROYED;
        }
        itemData.applyLevel(item, Math.max(0, level - 1));
        return Result.FAIL_DOWNGRADE;
    }

    /** Transcends the item in the player's main hand (replacing it when the material changes). */
    public Result transcend(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!itemData.isEnhanceable(item)) {
            return Result.NOT_ENHANCEABLE;
        }
        int stage = itemData.stage(item);
        int level = itemData.level(item);
        Optional<TranscendStage> next = config.transcendStage(stage + 1);
        if (next.isEmpty() || !TranscendRules.canTranscend(stage, level, config.maxLevel())) {
            return Result.NOT_AT_GATE;
        }
        TranscendStage target = next.get();
        UUID uuid = player.getUniqueId();
        if (!core.economyData().hasBalance(uuid, target.currency())) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (!EnhanceMaterialResolver.hasAmount(player, target.stoneItemId(), target.stoneAmount())) {
            return Result.INSUFFICIENT_STONE;
        }
        core.economyData().modifyBalance(uuid, -target.currency(), "YeowoolEnhance", "초월 시도 (" + target.name() + ")");
        EnhanceMaterialResolver.removeAmount(player, target.stoneItemId(), target.stoneAmount());

        if (ThreadLocalRandom.current().nextDouble(100) >= target.successRate()) {
            return Result.TRANSCEND_FAIL;
        }
        ItemStack result = item;
        int newLevel = level;
        if (target.stage() == 1) {
            newLevel = 0;
            Material netherite = Material.matchMaterial(TranscendRules.netheriteVariant(item.getType().name()));
            if (netherite != null && netherite != item.getType()) {
                result = item.withType(netherite);
            }
        }
        itemData.applyState(result, target.stage(), newLevel);
        player.getInventory().setItemInMainHand(result);
        return Result.TRANSCEND_SUCCESS;
    }
}
```

- [ ] **Step 2: `EnhanceMaterialResolver.java`** — add (the file already imports `CustomStack`, `Bukkit`, `ItemStack`, `Material`):

```java
    /** A fresh stack of {@code id} (vanilla Material name or ItemsAdder id), or null if it can't be resolved. */
    public static ItemStack createItem(String id, int amount) {
        if (isCustomItem(id)) {
            if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                return null;
            }
            CustomStack custom = CustomStack.getInstance(id);
            return custom == null ? null : custom.getItemStack().asQuantity(amount);
        }
        Material material = parseVanilla(id);
        return material == null ? null : new ItemStack(material, amount);
    }
```

- [ ] **Step 3: `EnhanceGui.java`**
  - Change the preview-empty text `"강화할 무기/방어구를 손에 드세요"` → `"강화할 무기/방어구/도구를 손에 드세요"`, and `"손에 무기나 방어구를 들어야 합니다."` → `"손에 무기, 방어구, 도구를 들어야 합니다."`.
  - Route the action button: in the constructor and in `refresh`, the `GuiButton.of(actionIcon(...), event -> attempt(...))` for `SLOT_ACTION` and the preview button handler call `onAction((Player) event.getWhoClicked())` instead of `attempt(...)`, with:

```java
    private void onAction(Player player) {
        if (service.isAtGate(player.getInventory().getItemInMainHand())) {
            transcend(player);
        } else {
            attempt(player);
        }
    }

    private void transcend(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int nextStage = service.itemData().stage(hand) + 1;
        var target = service.config().transcendStage(nextStage);
        EnhanceService.Result result = service.transcend(player);
        switch (result) {
            case TRANSCEND_SUCCESS -> {
                playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                String name = target.map(TranscendStage::name).orElse(nextStage + "차 초월");
                messages.send(player, nextStage == 1 ? "enhance.transcend-success-netherite" : "enhance.transcend-success",
                        Placeholder.unparsed("stage", name));
                messages.broadcast("enhance.transcend-broadcast",
                        Placeholder.unparsed("player", player.getName()), Placeholder.unparsed("stage", name));
            }
            case TRANSCEND_FAIL -> {
                playSound(player, Sound.ENTITY_VILLAGER_NO);
                messages.send(player, "enhance.transcend-fail");
            }
            case INSUFFICIENT_FUNDS -> messages.send(player, "enhance.insufficient-funds",
                    Placeholder.unparsed("cost", String.format("%,d", target.map(TranscendStage::currency).orElse(0L))));
            case INSUFFICIENT_STONE -> messages.send(player, "enhance.insufficient-stone",
                    Placeholder.unparsed("stone", target.map(t -> EnhanceMaterialResolver.displayName(t.stoneItemId())).orElse("초월석")),
                    Placeholder.unparsed("amount", String.valueOf(target.map(TranscendStage::stoneAmount).orElse(1))));
            case NOT_ENHANCEABLE -> messages.send(player, "enhance.not-enhanceable");
            default -> messages.send(player, "enhance.not-at-gate");
        }
        refresh(player);
    }
```

  - In `attempt`, add `case NEEDS_TRANSCEND -> messages.send(player, "enhance.needs-transcend");` to the switch, and replace `Placeholder.unparsed("cost", String.format("%,d", service.costs().costFor(level).currency()))` with `Placeholder.unparsed("cost", String.format("%,d", service.enhanceCurrency(hand)))`. Also: `EnhanceTier tier = service.config().tierFor(level);` stays; for the SUCCESS message use a grade name that respects transcendence — replace the SUCCESS case's `Placeholder.unparsed("tier", tier.name())` with `Placeholder.unparsed("tier", gradeName(hand))` and add:

```java
    private String gradeName(ItemStack item) {
        int stage = service.itemData().stage(item);
        if (stage > 0) {
            return service.config().transcendStage(stage).map(TranscendStage::name).orElse(stage + "차 초월");
        }
        return service.config().tierFor(service.itemData().level(item)).name();
    }
```

  - In `actionIcon`, at the top of the `else` branch (a held enhanceable item), before computing `level`, add a transcend view:

```java
            if (service.isAtGate(hand)) {
                return transcendIcon(hand);
            }
```

    and replace the stage-unaware parts of the normal branch: the max-level check becomes `if (level >= TranscendRules.levelCap(service.itemData().stage(hand), config.maxLevel()))`, the "현재" line uses `gradeName(hand)` with the grade color (use `NamedTextColor.GOLD` for simplicity), and `"필요 온: " + String.format("%,d", cost.currency())` becomes `"필요 온: " + String.format("%,d", service.enhanceCurrency(hand))`; the "성공 시 … 승급" line only shows when `service.itemData().stage(hand) == 0`. Add:

```java
    private ItemStack transcendIcon(ItemStack hand) {
        ItemStack stack = EnhanceIcons.resolveCustom("yeowool_enhance:enhance_confirm");
        if (stack == null) {
            stack = new ItemStack(Material.NETHER_STAR);
        }
        int nextStage = service.itemData().stage(hand) + 1;
        TranscendStage target = service.config().transcendStage(nextStage).orElseThrow();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("✦ 초월하기 — " + target.name(), target.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (nextStage == 1) {
            lore.add(Component.text("성공 시 네더라이트 장비로 바뀌고 0강부터 다시 강화합니다.", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("성공 시 강화 수치를 유지한 채 " + target.name() + "로 올라갑니다.", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("성공 확률: " + target.successRate() + "%", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("필요 온: " + String.format("%,d", target.currency()), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("필요 " + EnhanceMaterialResolver.displayName(target.stoneItemId()) + ": " + target.stoneAmount() + "개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("실패해도 장비는 그대로입니다. (초월석·온만 소모)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
```

- [ ] **Step 4: Create `TranscendStoneCommand.java`**

```java
package com.yeowool.enhance;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /초월석 지급 <닉네임> <1|2|3> [개수]} — staff/console (world boss rewards run it from the
 * console). Delivered through the mailbox, so the target may be offline or on another server as long
 * as they've joined before.
 */
public final class TranscendStoneCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_AMOUNT = 64;

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final EnhanceConfig config;

    public TranscendStoneCommand(YeowoolCoreAPI core, MessageService messages, EnhanceConfig config) {
        this.core = core;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equals("지급")) {
            messages.send(sender, "enhance.stone-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            messages.send(sender, "enhance.stone-unknown-player");
            return true;
        }
        int stage;
        int amount;
        try {
            stage = Integer.parseInt(args[2]);
            amount = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
        } catch (NumberFormatException e) {
            messages.send(sender, "enhance.stone-usage");
            return true;
        }
        var transcendStage = config.transcendStage(stage);
        if (transcendStage.isEmpty() || amount < 1 || amount > MAX_AMOUNT) {
            messages.send(sender, "enhance.stone-usage");
            return true;
        }
        ItemStack stone = EnhanceMaterialResolver.createItem(transcendStage.get().stoneItemId(), amount);
        if (stone == null) {
            messages.send(sender, "enhance.stone-missing-item", Placeholder.unparsed("id", transcendStage.get().stoneItemId()));
            return true;
        }
        core.mailbox().deliverOrStore(target.getUniqueId(), stone, "YeowoolEnhance", transcendStage.get().name() + " 초월석");
        messages.send(sender, "enhance.stone-given",
                Placeholder.unparsed("player", target.getName() == null ? args[1] : target.getName()),
                Placeholder.unparsed("stage", transcendStage.get().name()),
                Placeholder.unparsed("amount", String.valueOf(amount)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("지급");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[1])).toList();
        }
        if (args.length == 3) {
            return List.of("1", "2", "3");
        }
        return List.of();
    }
}
```

- [ ] **Step 5: Wire in `YeowoolEnhance.java`** — after the `강화설정` command block add:

```java
        var stoneCommand = getCommand("초월석");
        if (stoneCommand != null) {
            var executorCmd = new TranscendStoneCommand(core, messages, config);
            stoneCommand.setExecutor(executorCmd);
            stoneCommand.setTabCompleter(executorCmd);
        }
```

  and update the class javadoc's first sentence to mention 도구 and 초월: `장비 강화 시스템: {@code /강화} GUI에서 무기/방어구/도구를 강화하고, 30강부터는 초월석으로 초월(1차: 네더라이트, 10강에서 2차, 20강에서 3차)합니다.` (keep the rest).

- [ ] **Step 6: `messages.yml`** — change `not-enhanceable` to `"<red>강화할 수 없는 아이템입니다. (무기/방어구/도구만 강화 가능)</red>"` and `hand-empty` to `"<red>강화할 무기, 방어구, 도구를 손에 들고 사용하세요.</red>"`; add under `enhance:`:

```yaml
  needs-transcend: "<light_purple>이 장비는 초월해야 더 강화할 수 있습니다. 강화 화면의 초월하기를 눌러주세요.</light_purple>"
  not-at-gate: "<red>지금은 초월할 수 없습니다. (1차: 30강, 2차: 1차 초월 후 10강, 3차: 2차 초월 후 20강)</red>"
  insufficient-stone: "<red><stone>이(가) 부족합니다. (필요: <amount>개)</red>"
  transcend-success: "<gold>✦ 초월 성공! <stage> 달성!</gold>"
  transcend-success-netherite: "<gold>✦ 초월 성공! <stage> — 네더라이트 장비로 다시 태어났습니다! (0강부터 다시 강화)</gold>"
  transcend-fail: "<red>초월에 실패했습니다. 장비는 그대로이며 초월석과 온만 소모되었습니다.</red>"
  transcend-broadcast: "<gold>✦ <player>님이 <stage>에 성공했습니다!</gold>"
  stone-usage: "<gray>/초월석 지급 [닉네임] [1|2|3] [개수]</gray>"
  stone-unknown-player: "<red>한 번도 접속한 적 없는 플레이어입니다.</red>"
  stone-missing-item: "<red>초월석 아이템(<id>)을 찾을 수 없습니다. ItemsAdder 팩을 확인하세요.</red>"
  stone-given: "<green><player>님에게 <stage> 초월석 <amount>개를 지급했습니다. (접속 중이 아니면 우편함)</green>"
```

- [ ] **Step 7: `plugin.yml`** — update `강화:` description to `손에 든 무기/방어구/도구를 강화·초월하는 GUI를 엽니다`, and add:

```yaml
  초월석:
    description: 초월석을 지급합니다 (/초월석 지급 닉네임 단계 개수)
    permission: yeowool.admin
    default: op
```

- [ ] **Step 8: Help** — in `yeowool-core/src/main/resources/help.yml`, replace `      - "/강화 - 손에 든 무기/방어구 강화 GUI"` with `      - "/강화 - 손에 든 무기/방어구/도구 강화 GUI (30강부터 초월석으로 초월: 1차 네더라이트 → 10강 2차 → 20강 3차)"`, and after `      - "/강화설정 - 강화 단계별 비용 설정 GUI"` add `      - "/초월석 지급 [닉네임] [1|2|3] [개수] - 초월석 지급 (op)"`.

- [ ] **Step 9: Build** — `./gradlew :yeowool-enhance:build :yeowool-core:build` → BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceService.java yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceGui.java yeowool-enhance/src/main/java/com/yeowool/enhance/EnhanceMaterialResolver.java yeowool-enhance/src/main/java/com/yeowool/enhance/TranscendStoneCommand.java yeowool-enhance/src/main/java/com/yeowool/enhance/YeowoolEnhance.java yeowool-enhance/src/main/resources/messages.yml yeowool-enhance/src/main/resources/plugin.yml yeowool-core/src/main/resources/help.yml
git commit -m "Add transcendence to /강화 and /초월석 stone issuing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: World boss reward commands (`yeowool-raid`)

**Files:**
- Modify: `yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossService.java`
- Modify: `yeowool-raid/src/main/java/com/yeowool/raid/YeowoolRaid.java`
- Modify: `yeowool-raid/src/main/resources/config.yml`

**Interfaces:**
- Consumes: `WorldBossRules.Reward(UUID player, int rank, long amount)` (rank 0 = participation only).
- Produces: `WorldBossService.Settings` gains `Map<Integer, List<String>> rankCommands, List<String> participationCommands, double participationCommandChance` (appended as the last three components).

- [ ] **Step 1: `WorldBossService.java`**
  - Append the three components to `record Settings(...)`: `Map<Integer, List<String>> rankCommands, List<String> participationCommands, double participationCommandChance`.
  - In `onDeath`, right after `List<WorldBossRules.Reward> rewards = ...;` add `runRewardCommands(rewards);` and add the method:

```java
    /** Console commands per reward ({player} replaced) — e.g. transcendence stones via YeowoolEnhance. */
    private void runRewardCommands(List<WorldBossRules.Reward> rewards) {
        for (WorldBossRules.Reward reward : rewards) {
            String name = names.get(reward.player());
            if (name == null) {
                continue;
            }
            List<String> commands;
            if (reward.rank() > 0) {
                commands = settings.rankCommands().getOrDefault(reward.rank(), List.of());
            } else if (random.nextDouble() * 100 < settings.participationCommandChance()) {
                commands = settings.participationCommands();
            } else {
                commands = List.of();
            }
            for (String command : commands) {
                String resolved = command.replace("{player}", name);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "월드보스 보상 명령어 실행 실패: " + resolved, e);
                }
            }
        }
    }
```

  (`random`, `names`, `plugin`, `Level`, `Bukkit` already exist/are imported in this class; add `java.util.Map` import if missing.)

- [ ] **Step 2: `YeowoolRaid.enableWorldBoss`** — before building `settings`, parse:

```java
        Map<Integer, List<String>> rankCommands = new HashMap<>();
        var rankSection = getConfig().getConfigurationSection("world-boss.rank-commands");
        if (rankSection != null) {
            for (String key : rankSection.getKeys(false)) {
                try {
                    rankCommands.put(Integer.parseInt(key), rankSection.getStringList(key));
                } catch (NumberFormatException e) {
                    getLogger().warning("world-boss.rank-commands의 '" + key + "'는 순위 숫자여야 합니다 — 건너뜁니다.");
                }
            }
        }
```

  and append to the `new WorldBossService.Settings(` arguments (after the min-damage-percent argument):

```java
                rankCommands,
                getConfig().getStringList("world-boss.participation-commands"),
                getConfig().getDouble("world-boss.participation-command-chance", 20.0)
```

  Add imports `java.util.HashMap`, `java.util.Map` if missing.

- [ ] **Step 3: `config.yml`** — under `world-boss:`, after `min-damage-percent: 1`, add:

```yaml
  # 처치 시 콘솔로 실행할 보상 명령어({player} = 닉네임). 기본값은 YeowoolEnhance 초월석 지급
  # (/초월석 지급 닉네임 단계 개수). 순위(1~3위)는 rank-commands, 그 외 참여자는
  # participation-command-chance% 확률로 participation-commands.
  rank-commands:
    1: ["초월석 지급 {player} 3 1"]
    2: ["초월석 지급 {player} 2 1"]
    3: ["초월석 지급 {player} 1 2"]
  participation-commands: ["초월석 지급 {player} 1 1"]
  participation-command-chance: 20
```

- [ ] **Step 4: Build** — `./gradlew :yeowool-raid:build` → BUILD SUCCESSFUL (existing `WorldBossRulesTest` still passes).

- [ ] **Step 5: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossService.java yeowool-raid/src/main/java/com/yeowool/raid/YeowoolRaid.java yeowool-raid/src/main/resources/config.yml
git commit -m "Run configurable reward commands for world boss ranks and participants

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
