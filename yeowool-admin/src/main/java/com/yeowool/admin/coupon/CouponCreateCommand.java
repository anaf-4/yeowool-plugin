package com.yeowool.admin.coupon;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /쿠폰생성 <이름> <만료일(yyyy-MM-dd)>} — the reward is every non-empty
 * stack in the admin's main inventory (hotbar + 27 slots, not armor/off-hand)
 * at creation time, so one coupon can pay out several different items (same
 * "held item = payload" convention as {@code /여울관리 우편}, just generalized
 * from one slot to the whole inventory grid). Those slots are cleared once
 * the coupon is created, same as the old single-item version consumed the
 * held item. The coupon stays valid through the end of the given date.
 */
public final class CouponCreateCommand implements CommandExecutor {

    private final MessageService messages;
    private final CouponManager couponManager;

    public CouponCreateCommand(MessageService messages, CouponManager couponManager) {
        this.messages = messages;
        this.couponManager = couponManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "coupon.create-player-only");
            return true;
        }
        if (args.length != 2) {
            messages.send(sender, "coupon.create-usage");
            return true;
        }
        List<ItemStack> rewardItems = CouponInventoryPayload.take(player);
        if (rewardItems.isEmpty()) {
            messages.send(sender, "coupon.create-no-item");
            return true;
        }
        long expiresAt = CouponDateParser.parseExpiryMillis(args[1]);
        if (expiresAt <= 0) {
            messages.send(sender, "coupon.invalid-date");
            return true;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            messages.send(sender, "coupon.expiry-must-be-future");
            return true;
        }

        var result = couponManager.create(args[0], rewardItems, expiresAt);
        if (result == CouponManager.CreateResult.ALREADY_EXISTS) {
            CouponInventoryPayload.giveBack(player, rewardItems);
            messages.send(sender, "coupon.already-exists", Placeholder.unparsed("code", args[0]));
            return true;
        }
        messages.send(sender, "coupon.create-success",
                Placeholder.unparsed("code", args[0]),
                Placeholder.unparsed("expiry", args[1]),
                Placeholder.unparsed("count", String.valueOf(rewardItems.size())));
        return true;
    }
}
