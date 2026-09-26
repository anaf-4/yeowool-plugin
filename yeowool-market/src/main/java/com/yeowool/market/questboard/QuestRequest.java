package com.yeowool.market.questboard;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One row of {@code yw_quest_requests}; {@code sample} is a 1-item copy of what the requester wants. */
public record QuestRequest(long id, UUID requester, String requesterName, ItemStack sample, String itemLabel,
                           int quantity, int delivered, long rewardPerItem, String status, long createdAt, long expiresAt) {

    public static final String OPEN = "OPEN";

    public int remaining() {
        return quantity - delivered;
    }

    public boolean isOpen() {
        return OPEN.equals(status);
    }
}
