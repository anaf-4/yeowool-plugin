package com.yeowool.enhance;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core enhance-attempt logic — deducts cost, rolls success, and on failure
 * (only once {@link EnhanceConfig#isFailRisky} for the current level) rolls
 * destroy vs. downgrade vs. plain fail, checking {@link EnhanceConfig#protectionItemId()}
 * first. Mutates the given {@link ItemStack} in place, so callers must pass
 * the actual live stack (e.g. from {@code getItemInMainHand()}), not a copy.
 * Cost (온/재료) comes from {@link EnhanceCostManager}, not {@link EnhanceConfig} —
 * that's the part {@code /강화설정} can change live, in-game.
 */
public final class EnhanceService {

    public enum Result {
        SUCCESS, SUCCESS_TIER_UP, FAIL_SAFE, FAIL_PROTECTED, FAIL_DOWNGRADE, FAIL_DESTROYED,
        NOT_ENHANCEABLE, MAX_LEVEL, INSUFFICIENT_FUNDS, INSUFFICIENT_MATERIAL
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

    public Result attempt(Player player, ItemStack item) {
        if (!itemData.isEnhanceable(item)) {
            return Result.NOT_ENHANCEABLE;
        }
        int level = itemData.level(item);
        if (level >= config.maxLevel()) {
            return Result.MAX_LEVEL;
        }

        UUID uuid = player.getUniqueId();
        EnhanceCostManager.CostEntry cost = costs.costFor(level);
        if (!core.economyData().hasBalance(uuid, cost.currency())) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (!EnhanceMaterialResolver.hasAmount(player, cost.materialId(), cost.materialAmount())) {
            return Result.INSUFFICIENT_MATERIAL;
        }

        core.economyData().modifyBalance(uuid, -cost.currency(), "YeowoolEnhance",
                "강화 시도 (+" + level + " -> +" + (level + 1) + ")");
        EnhanceMaterialResolver.removeAmount(player, cost.materialId(), cost.materialAmount());

        boolean success = ThreadLocalRandom.current().nextDouble(100) < config.successRate(level);
        if (success) {
            boolean tierUp = config.crossesTierAt(level);
            itemData.applyLevel(item, level + 1);
            return tierUp ? Result.SUCCESS_TIER_UP : Result.SUCCESS;
        }

        if (!config.isFailRisky(level)) {
            return Result.FAIL_SAFE;
        }
        double roll = ThreadLocalRandom.current().nextDouble(100);
        boolean destroy = roll < config.failDestroyChancePercent();
        boolean downgrade = !destroy && roll < config.failDestroyChancePercent() + config.failDowngradeChancePercent();
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
}
