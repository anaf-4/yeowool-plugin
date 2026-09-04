package com.yeowool.admin.coupon;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code /쿠폰관리 목록}, {@code /쿠폰관리 <이름> 정보|제거|수정 아이템|수정 기간 <yyyy-MM-dd>}.
 * "수정" takes an explicit target (아이템/기간) rather than overloading a
 * single bare "수정" so it's unambiguous which part of the coupon changes —
 * matching {@code /토지 권한}'s explicit-subargument style elsewhere in this
 * codebase.
 */
public final class CouponManageCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MessageService messages;
    private final CouponManager couponManager;

    public CouponManageCommand(MessageService messages, CouponManager couponManager) {
        this.messages = messages;
        this.couponManager = couponManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("목록")) {
            list(sender);
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "coupon.manage-usage");
            return true;
        }

        String code = args[0];
        switch (args[1]) {
            case "정보" -> info(sender, code);
            case "제거" -> remove(sender, code);
            case "수정" -> edit(sender, code, args);
            default -> messages.send(sender, "coupon.manage-sub-usage");
        }
        return true;
    }

    private void list(CommandSender sender) {
        var coupons = couponManager.all();
        if (coupons.isEmpty()) {
            messages.send(sender, "coupon.manage-list-empty");
            return;
        }
        messages.send(sender, "coupon.manage-list-header", Placeholder.unparsed("count", String.valueOf(coupons.size())));
        for (var coupon : coupons) {
            String status = coupon.isExpired() ? "만료됨" : "사용 가능";
            messages.send(sender, "coupon.manage-list-line",
                    Placeholder.unparsed("code", coupon.code()),
                    Placeholder.unparsed("item", coupon.rewardItem().getType().toString()),
                    Placeholder.unparsed("status", status),
                    Placeholder.unparsed("expiry", FORMAT.format(Instant.ofEpochMilli(coupon.expiresAt()))));
        }
    }

    private void info(CommandSender sender, String code) {
        var coupon = couponManager.findByCode(code);
        if (coupon.isEmpty()) {
            messages.send(sender, "coupon.not-found", Placeholder.unparsed("code", code));
            return;
        }
        var c = coupon.get();
        messages.send(sender, "coupon.manage-info-name", Placeholder.unparsed("code", c.code()));
        messages.send(sender, "coupon.manage-info-reward",
                Placeholder.unparsed("item", c.rewardItem().getType().toString()),
                Placeholder.unparsed("amount", String.valueOf(c.rewardItem().getAmount())));
        messages.send(sender, "coupon.manage-info-created",
                Placeholder.unparsed("created", FORMAT.format(Instant.ofEpochMilli(c.createdAt()))));
        messages.send(sender, "coupon.manage-info-expiry",
                Placeholder.unparsed("expiry", FORMAT.format(Instant.ofEpochMilli(c.expiresAt()))),
                Placeholder.unparsed("status", c.isExpired() ? " (만료됨)" : ""));
    }

    private void remove(CommandSender sender, String code) {
        if (!couponManager.delete(code)) {
            messages.send(sender, "coupon.not-found", Placeholder.unparsed("code", code));
            return;
        }
        messages.send(sender, "coupon.manage-remove-success", Placeholder.unparsed("code", code));
    }

    private void edit(CommandSender sender, String code, String[] args) {
        if (couponManager.findByCode(code).isEmpty()) {
            messages.send(sender, "coupon.not-found", Placeholder.unparsed("code", code));
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "coupon.manage-edit-usage");
            return;
        }
        switch (args[2]) {
            case "아이템" -> editItem(sender, code);
            case "기간" -> editDuration(sender, code, args);
            default -> messages.send(sender, "coupon.manage-edit-usage");
        }
    }

    private void editItem(CommandSender sender, String code) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "coupon.manage-edit-item-player-only");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            messages.send(sender, "coupon.manage-edit-item-no-item");
            return;
        }
        couponManager.updateRewardItem(code, hand);
        messages.send(sender, "coupon.manage-edit-item-success",
                Placeholder.unparsed("code", code),
                Placeholder.unparsed("item", hand.getType().toString()));
    }

    private void editDuration(CommandSender sender, String code, String[] args) {
        if (args.length != 4) {
            messages.send(sender, "coupon.manage-edit-duration-usage");
            return;
        }
        long expiresAt = CouponDateParser.parseExpiryMillis(args[3]);
        if (expiresAt <= 0) {
            messages.send(sender, "coupon.invalid-date");
            return;
        }
        couponManager.updateExpiry(code, expiresAt);
        messages.send(sender, "coupon.manage-edit-duration-success",
                Placeholder.unparsed("code", code),
                Placeholder.unparsed("expiry", args[3]));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> codes = couponManager.all().stream().map(Coupon::code).toList();
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(codes);
            options.add("목록");
            return TabCompletions.filter(options, args[0]);
        }
        if (args.length == 2 && !args[0].equals("목록")) {
            return TabCompletions.filter(List.of("정보", "제거", "수정"), args[1]);
        }
        if (args.length == 3 && args[1].equals("수정")) {
            return TabCompletions.filter(List.of("아이템", "기간"), args[2]);
        }
        return List.of();
    }
}
