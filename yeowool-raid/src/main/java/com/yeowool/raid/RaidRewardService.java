package com.yeowool.raid;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

final class RaidRewardService {

    private static final Random RANDOM = new Random();

    private RaidRewardService() {
    }

    static void completeVictory(RaidManager raidManager, RaidSession session, RaidDefinition raid, YeowoolCoreAPI core) {
        for (UUID member : session.getPartyMembers()) {
            Optional<ItemStack> reward = RaidRewardRoller.roll(raid.rewardItems(), RANDOM);
            reward.ifPresent(item -> core.mailbox().deliverOrStore(member, item, "YeowoolRaid", raid.name() + " 클리어 보상"));
        }
        raidManager.endSession(session, RaidSessionState.WON);
    }
}
