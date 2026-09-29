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
        TRANSCEND_SUCCESS, TRANSCEND_SUCCESS_NEW_MATERIAL, TRANSCEND_FAIL, NOT_AT_GATE, INSUFFICIENT_STONE
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
        if (!core.economyData().modifyBalance(uuid, -target.currency(), "YeowoolEnhance", "초월 시도 (" + target.name() + ")")) {
            return Result.INSUFFICIENT_FUNDS;
        }
        EnhanceMaterialResolver.removeAmount(player, target.stoneItemId(), target.stoneAmount());

        if (ThreadLocalRandom.current().nextDouble(100) >= target.successRate()) {
            return Result.TRANSCEND_FAIL;
        }
        ItemStack result = item;
        int newLevel = level;
        Material originalType = item.getType();
        if (target.stage() == 1) {
            newLevel = 0;
            Material netherite = Material.matchMaterial(TranscendRules.netheriteVariant(item.getType().name()));
            if (netherite != null && netherite != item.getType()) {
                result = item.withType(netherite);
            }
        }
        itemData.applyState(result, target.stage(), newLevel);
        player.getInventory().setItemInMainHand(result);
        return result.getType() != originalType ? Result.TRANSCEND_SUCCESS_NEW_MATERIAL : Result.TRANSCEND_SUCCESS;
    }
}
