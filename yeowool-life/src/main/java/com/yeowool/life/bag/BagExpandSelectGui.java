package com.yeowool.life.bag;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Opened by right-clicking {@code moafarm_items:ticket_chest} — pick one of
 * the 4 bags to expand by 1 slot, consuming the ticket from the player's
 * main hand.
 */
public final class BagExpandSelectGui extends YeowoolGui {

    private static final int[] SLOTS = {10, 12, 14, 16};
    private static final int EXPAND_BY = 1;

    public BagExpandSelectGui(BagManager manager, MessageService messages, Player viewer) {
        super(27, Component.text("가방 확장 - 어떤 가방을 늘릴까요?", NamedTextColor.GOLD));

        BagType[] types = BagType.values();
        for (int i = 0; i < types.length; i++) {
            BagType type = types[i];
            boolean maxed = manager.isAtMaxCapacity(viewer.getUniqueId(), type);
            int used = manager.usedSlots(viewer.getUniqueId(), type);
            int capacity = manager.capacity(viewer.getUniqueId(), type);

            ItemStack icon = new ItemStack(type.menuIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(type.label(), NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("현재: " + used + "/" + capacity + "칸", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    maxed
                            ? Component.text("이미 최대 칸수입니다.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                            : Component.text("클릭 시 " + capacity + " → " + (capacity + EXPAND_BY) + "칸으로 확장", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
            icon.setItemMeta(meta);

            setButton(SLOTS[i], GuiButton.of(icon, event -> {
                if (maxed) {
                    return;
                }
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!TicketChestListener.isTicket(hand)) {
                    messages.send(player, "bag.expand-no-ticket");
                    return;
                }
                manager.expand(player.getUniqueId(), type, EXPAND_BY);
                if (hand.getAmount() <= 1) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    hand.setAmount(hand.getAmount() - 1);
                }
                messages.send(player, "bag.expand-success", Placeholder.unparsed("bag", type.label()));
                player.closeInventory();
            }));
        }
    }
}
