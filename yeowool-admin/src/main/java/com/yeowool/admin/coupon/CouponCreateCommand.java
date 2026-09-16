package com.yeowool.admin.coupon;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /쿠폰생성 <이름> <만료일(yyyy-MM-dd)>} — opens a 54-slot
 * {@link ItemGridEditorGui}; whatever's in it when the admin closes it becomes
 * the coupon's reward list, so one coupon can pay out several different
 * items. The coupon stays valid through the end of the given date.
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
        String code = args[0];
        if (couponManager.findByCode(code).isPresent()) {
            messages.send(sender, "coupon.already-exists", Placeholder.unparsed("code", code));
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

        new ItemGridEditorGui("쿠폰 보상 설정 (닫으면 저장됩니다)", List.of(), rewardItems -> {
            if (rewardItems.isEmpty()) {
                messages.send(player, "coupon.create-no-item");
                return;
            }
            var result = couponManager.create(code, rewardItems, expiresAt);
            if (result == CouponManager.CreateResult.ALREADY_EXISTS) {
                giveBack(player, rewardItems);
                messages.send(player, "coupon.already-exists", Placeholder.unparsed("code", code));
                return;
            }
            messages.send(player, "coupon.create-success",
                    Placeholder.unparsed("code", code),
                    Placeholder.unparsed("expiry", args[1]),
                    Placeholder.unparsed("count", String.valueOf(rewardItems.size())));
        }).open(player);
        return true;
    }

    private static void giveBack(Player player, List<ItemStack> items) {
        var leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
}
