package com.yeowool.raid;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Each winning participant rolls their own single item, uniformly at random, from the reward pool. */
public final class RaidRewardRoller {

    private RaidRewardRoller() {
    }

    public static Optional<ItemStack> roll(List<ItemStack> pool, Random random) {
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        ItemStack picked = pool.get(random.nextInt(pool.size()));
        return Optional.of(picked.clone());
    }
}
