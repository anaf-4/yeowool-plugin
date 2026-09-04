package com.yeowool.admin.coupon;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /쿠폰생성 <이름> <만료일(yyyy-MM-dd)>} — the reward is whatever's in
 * the admin's main hand at creation time (same "held item = payload"
 * convention as {@code /여울관리 우편}), so this stays a 2-argument command
 * exactly as asked rather than needing an inline item description. The
 * coupon stays valid through the end of the given date.
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
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

        var result = couponManager.create(args[0], hand, expiresAt);
        if (result == CouponManager.CreateResult.ALREADY_EXISTS) {
            messages.send(sender, "coupon.already-exists", Placeholder.unparsed("code", args[0]));
            return true;
        }
        messages.send(sender, "coupon.create-success",
                Placeholder.unparsed("code", args[0]),
                Placeholder.unparsed("expiry", args[1]),
                Placeholder.unparsed("item", hand.getType().toString()));
        return true;
    }
}
