package com.yeowool.raid;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record RaidDefinition(
        long id,
        String name,
        int npcId,
        String mythicMobId,
        String ticketItemId,
        int ticketAmount,
        int minPartySize,
        int maxPartySize,
        int timeLimitSeconds,
        int sharedLives,
        int instanceCount,
        List<ItemStack> rewardItems
) {
    public RaidDefinition withNpcId(int npcId) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withMythicMob(String mythicMobId) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withTicket(String ticketItemId, int ticketAmount) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withPartySize(int minPartySize, int maxPartySize) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withTimeLimit(int timeLimitSeconds) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withSharedLives(int sharedLives) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withInstanceCount(int instanceCount) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withRewardItems(List<ItemStack> rewardItems) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }
}
