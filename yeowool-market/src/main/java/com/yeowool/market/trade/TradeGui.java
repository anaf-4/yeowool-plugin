package com.yeowool.market.trade;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One viewer's half of a {@link TradeSession}. Rows 1 (slots 9-17) are the
 * viewer's own editable offer; rows 3 (27-35) mirror the other side's offer
 * and are never editable. See {@link com.yeowool.core.api.gui.YeowoolGui}
 * for how editable-slot clicks get routed through without being cancelled.
 */
public final class TradeGui extends YeowoolGui {

    private static final int[] MY_ITEM_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] THEIR_ITEM_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int MY_MONEY_SLOT = 38;
    private static final int THEIR_MONEY_SLOT = 42;
    private static final int CANCEL_SLOT = 48;
    private static final int CONFIRM_SLOT = 50;

    private final TradeSession session;
    private final UUID viewer;
    private volatile boolean suppressCloseCancel;

    public TradeGui(TradeSession session, UUID viewer) {
        super(54, Component.text("거래", NamedTextColor.DARK_PURPLE));
        this.session = session;
        this.viewer = viewer;

        for (int slot = 0; slot < 54; slot++) {
            if (isDecorationSlot(slot)) {
                setButton(slot, GuiButton.display(pane()));
            }
        }
        for (int slot : MY_ITEM_SLOTS) {
            setEditableSlot(slot);
        }

        setButton(MY_MONEY_SLOT, GuiButton.of(moneyItem("내 제안 금액", session.moneyFor(viewer)), event ->
                TradeChatInputListener.awaitInput(this, session, viewer)));
        setButton(CANCEL_SLOT, GuiButton.of(namedItem(Material.RED_WOOL, "취소"), event ->
                session.cancel("trade.cancelled")));
        setButton(CONFIRM_SLOT, GuiButton.of(confirmItem(), event -> session.confirm(viewer)));

        refresh();
    }

    private boolean isDecorationSlot(int slot) {
        return !contains(MY_ITEM_SLOTS, slot) && !contains(THEIR_ITEM_SLOTS, slot)
                && slot != MY_MONEY_SLOT && slot != THEIR_MONEY_SLOT && slot != CANCEL_SLOT && slot != CONFIRM_SLOT;
    }

    private boolean contains(int[] array, int value) {
        return Arrays.stream(array).anyMatch(v -> v == value);
    }

    /**
     * Re-renders everything except the editable "my items" region: the other
     * side's mirrored items, both money amounts, and the confirm button's
     * on/off state.
     */
    public void refresh() {
        ItemStack[] theirItems = session.itemsFor(session.other(viewer));
        for (int i = 0; i < THEIR_ITEM_SLOTS.length; i++) {
            getInventory().setItem(THEIR_ITEM_SLOTS[i], theirItems[i]);
        }
        setButton(MY_MONEY_SLOT, GuiButton.of(moneyItem("내 제안 금액 (클릭하여 변경)", session.moneyFor(viewer)), event ->
                TradeChatInputListener.awaitInput(this, session, viewer)));
        setButton(THEIR_MONEY_SLOT, GuiButton.display(moneyItem("상대 제안 금액", session.moneyFor(session.other(viewer)))));
        setButton(CONFIRM_SLOT, GuiButton.of(confirmItem(), event -> session.confirm(viewer)));
    }

    @Override
    public void onEditableSlotChanged() {
        ItemStack[] snapshot = new ItemStack[MY_ITEM_SLOTS.length];
        for (int i = 0; i < MY_ITEM_SLOTS.length; i++) {
            ItemStack item = getInventory().getItem(MY_ITEM_SLOTS[i]);
            snapshot[i] = item == null ? null : item.clone();
        }
        session.onItemsChanged(viewer, snapshot);
    }

    @Override
    public void onClose(Player player) {
        if (!suppressCloseCancel && !session.isFinished()) {
            session.cancel("trade.cancelled-disconnect");
        }
    }

    void prepareForChatInput(Player player) {
        suppressCloseCancel = true;
        player.closeInventory();
    }

    void reopen(Player player) {
        suppressCloseCancel = false;
        refresh();
        open(player);
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack moneyItem(String title, long amount) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(String.format("%,d", amount) + "온", NamedTextColor.YELLOW)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack confirmItem() {
        boolean confirmed = session.isConfirmed(viewer);
        ItemStack stack = new ItemStack(confirmed ? Material.LIME_WOOL : Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(confirmed ? "확인함 (대기 중)" : "확인", confirmed ? NamedTextColor.GREEN : NamedTextColor.WHITE));
        stack.setItemMeta(meta);
        return stack;
    }
}
