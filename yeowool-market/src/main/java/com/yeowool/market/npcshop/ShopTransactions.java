package com.yeowool.market.npcshop;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.util.ItemResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The economy side of buying/selling a {@link ShopItem}, shared by
 * {@link NPCShopGui} (instant sell-all on shift-right/middle click) and
 * {@link AmountSelectionGui} (buy/sell a chosen quantity) so there is one
 * place that ever touches a player's balance for an NPC shop trade.
 */
public final class ShopTransactions {

    private ShopTransactions() {
    }

    public static boolean hasFunds(YeowoolCoreAPI core, Player player, ShopItem item, long cost) {
        return item.currency() == CurrencyType.CASH
                ? core.economyData().hasCashBalance(player.getUniqueId(), cost)
                : core.economyData().hasBalance(player.getUniqueId(), cost);
    }

    private static void chargeCost(YeowoolCoreAPI core, Player player, ShopItem item, long cost, String reason) {
        if (item.currency() == CurrencyType.CASH) {
            core.economyData().modifyCashBalance(player.getUniqueId(), -cost, "YeowoolMarket", reason);
        } else {
            core.economyData().modifyBalance(player.getUniqueId(), -cost, "YeowoolMarket", reason);
        }
    }

    private static void payEarnings(YeowoolCoreAPI core, Player player, ShopItem item, long earned, String reason) {
        if (item.currency() == CurrencyType.CASH) {
            core.economyData().modifyCashBalance(player.getUniqueId(), earned, "YeowoolMarket", reason);
        } else {
            core.economyData().modifyBalance(player.getUniqueId(), earned, "YeowoolMarket", reason);
        }
    }

    public static int countMatching(Player player, ShopItem item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (ItemResolver.matches(stack, item)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /**
     * Whether {@code amount} more of {@code sample} would actually fit —
     * unlike a plain {@code firstEmpty() == -1} check, this also counts room
     * left in the player's own already-partial stacks of the same item, so
     * a purchase that would just top off an existing stack isn't wrongly
     * rejected just because no slot is fully empty.
     */
    private static boolean hasRoomFor(Player player, ItemStack sample, int amount) {
        int remaining = amount;
        int maxStack = sample.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                remaining -= maxStack;
            } else if (stack.isSimilar(sample)) {
                remaining -= Math.max(0, maxStack - stack.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private static void removeMatching(Player player, ShopItem item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (remaining <= 0) {
                break;
            }
            if (ItemResolver.matches(stack, item)) {
                int take = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                remaining -= take;
            }
        }
    }

    public static boolean buy(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, Player player, ShopItem item, int amount) {
        if (!item.isBuyable()) {
            messages.send(player, "npcshop.not-buyable");
            return false;
        }
        long cost = item.buyPrice() * amount;
        if (!hasFunds(core, player, item, cost)) {
            messages.send(player, "npcshop.insufficient-funds");
            return false;
        }
        ItemStack sample = ItemResolver.build(item, 1, plugin.getLogger());
        if (!hasRoomFor(player, sample, amount)) {
            messages.send(player, "npcshop.inventory-full");
            return false;
        }
        chargeCost(core, player, item, cost, "NPC 상점 구매: " + item.displayId());
        player.getInventory().addItem(ItemResolver.build(item, amount, plugin.getLogger()));
        core.sounds().play(player, "success");
        messages.send(player, "npcshop.buy-success",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("item", item.displayId()),
                Placeholder.unparsed("cost", String.format("%,d", cost)),
                Placeholder.unparsed("currency", item.currency().displayName()));
        return true;
    }

    /** Sells exactly {@code amount} matching items (fails if the player owns fewer than that). */
    public static boolean sellAmount(YeowoolCoreAPI core, MessageService messages, Player player, ShopItem item, int amount) {
        if (!item.isSellable()) {
            messages.send(player, "npcshop.not-sellable");
            return false;
        }
        if (countMatching(player, item) < amount) {
            messages.send(player, "npcshop.nothing-to-sell");
            return false;
        }
        removeMatching(player, item, amount);
        long earned = item.sellPrice() * amount;
        payEarnings(core, player, item, earned, "NPC 상점 판매: " + item.displayId());
        core.sounds().play(player, "success");
        messages.send(player, "npcshop.sell-success",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("item", item.displayId()),
                Placeholder.unparsed("earned", String.format("%,d", earned)),
                Placeholder.unparsed("currency", item.currency().displayName()));
        return true;
    }

    /** Sells every matching item currently in the player's inventory. */
    public static boolean sellAll(YeowoolCoreAPI core, MessageService messages, Player player, ShopItem item) {
        if (!item.isSellable()) {
            messages.send(player, "npcshop.not-sellable");
            return false;
        }
        int count = countMatching(player, item);
        if (count == 0) {
            messages.send(player, "npcshop.nothing-to-sell");
            return false;
        }
        removeMatching(player, item, count);
        long earned = item.sellPrice() * count;
        payEarnings(core, player, item, earned, "NPC 상점 판매: " + item.displayId());
        core.sounds().play(player, "success");
        messages.send(player, "npcshop.sell-success",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("item", item.displayId()),
                Placeholder.unparsed("earned", String.format("%,d", earned)),
                Placeholder.unparsed("currency", item.currency().displayName()));
        return true;
    }
}
