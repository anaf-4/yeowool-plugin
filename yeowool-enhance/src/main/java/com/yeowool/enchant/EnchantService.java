package com.yeowool.enchant;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core buy/dismantle/fuse logic for {@code /인챈트강화} — a completely
 * separate system from {@code /강화}'s +N강 (see {@code com.yeowool.enhance}),
 * modeled after AdvancedEnchantments' own Enchanter/Tinkerer/Alchemist loop:
 * buy a random-rarity book with 온 (a real vanilla enchanted book, usable in
 * a normal anvil), drag unwanted books into the Tinkerer for 마법 가루 + a
 * partial 온 refund, or drag two same-tier books into the Alchemist to fuse
 * them into one book of the next tier.
 */
public final class EnchantService {

    public enum BuyResult { SUCCESS, INSUFFICIENT_FUNDS }

    private final YeowoolCoreAPI core;
    private final EnchantConfig config;
    private final EnchantItems items;

    public EnchantService(YeowoolCoreAPI core, EnchantConfig config, EnchantItems items) {
        this.core = core;
        this.config = config;
        this.items = items;
    }

    public EnchantConfig config() {
        return config;
    }

    public EnchantItems items() {
        return items;
    }

    public BuyResult buy(Player player, EnchantTier tier) {
        var setting = config.settingFor(tier);
        if (!core.economyData().hasBalance(player.getUniqueId(), setting.cost())) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        core.economyData().modifyBalance(player.getUniqueId(), -setting.cost(), "YeowoolEnhance",
                "인챈트강화 구매: " + tier.name());
        items.give(player, items.createBook(tier));
        return BuyResult.SUCCESS;
    }

    /** Null if {@code a}/{@code b} aren't both valid same-tier books, or that tier is already {@link EnchantTier#FABLED}. */
    public ItemStack previewFuse(ItemStack a, ItemStack b) {
        EnchantTier tierA = items.tierOf(a);
        EnchantTier tierB = items.tierOf(b);
        if (tierA == null || tierB == null || tierA != tierB) {
            return null;
        }
        EnchantTier next = tierA.next();
        return next == null ? null : items.createBook(next);
    }

    /** One Tinkerer offer: a dust stack per deposited book (front-to-back) plus the combined 온 refund. */
    public record TinkererOffer(List<ItemStack> dustStacks, long totalRefund) {
    }

    public TinkererOffer previewTinkererOffer(List<ItemStack> books) {
        List<ItemStack> dustStacks = new ArrayList<>();
        long totalRefund = 0;
        for (ItemStack book : books) {
            EnchantTier tier = items.tierOf(book);
            if (tier == null) {
                continue;
            }
            var setting = config.settingFor(tier);
            int dust = ThreadLocalRandom.current().nextInt(setting.dustMin(), setting.dustMax() + 1);
            dustStacks.add(items.createDust(dust));
            totalRefund += Math.round(setting.cost() * (config.dismantleRefundPercent() / 100.0));
        }
        return new TinkererOffer(dustStacks, totalRefund);
    }

    /** Gives the player everything in {@code offer} — caller is responsible for having already cleared the deposited books. */
    public void confirmTinkererOffer(Player player, TinkererOffer offer) {
        for (ItemStack dust : offer.dustStacks()) {
            items.give(player, dust);
        }
        if (offer.totalRefund() > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), offer.totalRefund(), "YeowoolEnhance", "인챈트강화 틴커러 거래");
        }
    }
}
