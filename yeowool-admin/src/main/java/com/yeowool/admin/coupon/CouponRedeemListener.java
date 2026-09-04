package com.yeowool.admin.coupon;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Coupon redemption is a two-step anvil interaction, not a one-shot typing
 * action: {@link PrepareAnvilEvent} only previews the reward as the anvil's
 * result item when the typed text matches a code, and the actual grant only
 * happens on {@link InventoryClickEvent} against that result slot (2).
 * Separating preview from confirm avoids PrepareAnvilEvent firing more than
 * once for the same finished text (it can re-fire on unrelated inventory
 * recalculation), which previously redeemed a coupon and then immediately
 * rejected the same redemption in the same tick ("사용했습니다" followed by
 * "이미 사용한 쿠폰입니다").
 */
public final class CouponRedeemListener implements Listener {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final CouponManager couponManager;

    public CouponRedeemListener(JavaPlugin plugin, MessageService messages, CouponManager couponManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.couponManager = couponManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        String renameText = event.getView().getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        var coupon = couponManager.findByCode(renameText);
        if (coupon.isEmpty()) {
            return;
        }
        event.setResult(previewItem(coupon.get()));
    }

    private ItemStack previewItem(Coupon coupon) {
        ItemStack preview = coupon.rewardItem().clone();
        ItemMeta meta = preview.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text("쿠폰: " + coupon.code(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add((coupon.isExpired()
                ? Component.text("만료된 쿠폰입니다", NamedTextColor.RED)
                : Component.text("클릭하여 쿠폰 사용", NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory) || event.getSlot() != 2) {
            return;
        }
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        String renameText = anvilView.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        var coupon = couponManager.findByCode(renameText);
        if (coupon.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (coupon.get().isExpired()) {
            messages.send(player, "coupon.redeem-expired", Placeholder.unparsed("code", coupon.get().code()));
            return;
        }
        if (couponManager.isRedeemed(coupon.get().code(), player.getUniqueId())) {
            messages.send(player, "coupon.redeem-already-used", Placeholder.unparsed("code", coupon.get().code()));
            return;
        }

        couponManager.markRedeemed(coupon.get().code(), player.getUniqueId());
        giveReward(player, coupon.get().rewardItem());
        messages.send(player, "coupon.redeem-success", Placeholder.unparsed("code", coupon.get().code()));

        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    private void giveReward(Player player, ItemStack rewardItem) {
        var leftover = player.getInventory().addItem(rewardItem.clone());
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }

    /** Discards {@code /쿠폰}'s "type here" placeholder on close instead of letting the anvil hand it back to the player. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof AnvilInventory anvil
                && CouponAnvilPlaceholder.isPlaceholder(plugin, anvil.getItem(0))) {
            anvil.setItem(0, null);
        }
    }
}
