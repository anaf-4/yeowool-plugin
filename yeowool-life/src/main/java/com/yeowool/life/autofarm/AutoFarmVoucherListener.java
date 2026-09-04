package com.yeowool.life.autofarm;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a 자동줍기권/자동심기권 consumes exactly 1 item from the held
 * stack and charges up {@link AutoFarmManager} by that item's embedded
 * amount (see {@link AutoFarmVoucherItem}); shift-right-click consumes the
 * entire held stack at once — however many are in it — charging up the sum
 * in one go.
 */
public final class AutoFarmVoucherListener implements Listener {

    private final AutoFarmManager manager;
    private final AutoFarmVoucherItem voucherItem;
    private final MessageService messages;

    public AutoFarmVoucherListener(AutoFarmManager manager, AutoFarmVoucherItem voucherItem, MessageService messages) {
        this.manager = manager;
        this.voucherItem = voucherItem;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        AutoFarmType type = voucherItem.typeOf(hand);
        if (type == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        var data = manager.core().playerData().getOnline(player.getUniqueId());
        long chargesPerItem = voucherItem.chargesOf(hand);
        boolean infinite = chargesPerItem >= AutoFarmType.INFINITE;

        int stackAmount = hand.getAmount();
        int consumed = player.isSneaking() ? stackAmount : 1;
        long grantAmount = infinite ? -1 : chargesPerItem * consumed;

        manager.grant(player, data, type, grantAmount);

        int leftover = stackAmount - consumed;
        if (leftover <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            ItemStack updated = hand.clone();
            updated.setAmount(leftover);
            player.getInventory().setItemInMainHand(updated);
        }

        String usedText = infinite ? "무제한" : String.format("%,d", grantAmount);
        messages.send(player, "autofarm.voucher-used",
                Placeholder.unparsed("type", type.label()),
                Placeholder.unparsed("amount", usedText));
    }
}
