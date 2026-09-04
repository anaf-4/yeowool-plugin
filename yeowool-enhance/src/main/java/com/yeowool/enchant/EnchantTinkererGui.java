package com.yeowool.enchant;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /인챈트강화}'s Tinkerer screen (9x6, matching AE's own tinkerer.yml)
 * — drop enchant books into any of the 23 input slots (left half of each
 * row), and a 마법 가루 stack appears in the next free output slot (right
 * half of each row, front-to-back) for each valid book. Slot 0 starts as
 * unconfirm; click once to arm the trade, click again to finalize (consumes
 * every deposited book, gives the shown dust + a partial 온 refund). Any
 * change to the deposited books disarms it back to unconfirm. Non-book items
 * dropped into an input slot are rejected. Background {@code yeowool_enhance:tinkerer_bg}.
 */
public final class EnchantTinkererGui extends YeowoolGui {

    private static final int SLOT_CONFIRM = 0;
    private static final int[] INPUT_SLOTS = {
            1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39, 45, 46, 47, 48
    };
    private static final int[] OUTPUT_SLOTS = {
            5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44, 50, 51, 52, 53
    };

    private final EnchantService service;
    private final MessageService messages;
    private final Player viewer;
    private EnchantService.TinkererOffer pendingOffer;
    private boolean armed;

    public EnchantTinkererGui(EnchantService service, MessageService messages, Player viewer) {
        super(54, EnchantBackgroundImages.title(service.config().backgroundOffsetPx(), "tinkerer_bg",
                Component.text("틴커러", NamedTextColor.AQUA)));
        this.service = service;
        this.messages = messages;
        this.viewer = viewer;

        for (int slot : INPUT_SLOTS) {
            setEditableSlot(slot);
        }
        setButton(SLOT_CONFIRM, GuiButton.of(confirmIcon(), event -> onConfirmClick((Player) event.getWhoClicked())));
        refreshOffer();
    }

    @Override
    public void onEditableSlotChanged() {
        rejectNonBooks();
        refreshOffer();
    }

    @Override
    public void onClose(Player player) {
        for (int slot : INPUT_SLOTS) {
            ItemStack item = getInventory().getItem(slot);
            if (item != null) {
                getInventory().setItem(slot, null);
                service.items().give(viewer, item);
            }
        }
    }

    private void rejectNonBooks() {
        for (int slot : INPUT_SLOTS) {
            ItemStack item = getInventory().getItem(slot);
            if (item != null && !service.items().isBook(item)) {
                getInventory().setItem(slot, null);
                service.items().give(viewer, item);
            }
        }
    }

    private void refreshOffer() {
        List<ItemStack> books = new ArrayList<>();
        for (int slot : INPUT_SLOTS) {
            ItemStack item = getInventory().getItem(slot);
            if (item != null && service.items().isBook(item)) {
                books.add(item);
            }
        }
        pendingOffer = books.isEmpty() ? null : service.previewTinkererOffer(books);

        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            ItemStack dust = pendingOffer != null && i < pendingOffer.dustStacks().size() ? pendingOffer.dustStacks().get(i) : null;
            getInventory().setItem(OUTPUT_SLOTS[i], dust);
        }
        setArmed(false);
    }

    private void onConfirmClick(Player player) {
        if (pendingOffer == null) {
            messages.send(player, "inchant.tinkerer-nothing-to-trade");
            return;
        }
        if (!armed) {
            setArmed(true);
            player.updateInventory();
            return;
        }

        for (int slot : INPUT_SLOTS) {
            getInventory().setItem(slot, null);
        }
        for (int slot : OUTPUT_SLOTS) {
            getInventory().setItem(slot, null);
        }
        service.confirmTinkererOffer(player, pendingOffer);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        messages.send(player, "inchant.tinkerer-trade-success");
        pendingOffer = null;
        setArmed(false);
        player.updateInventory();
    }

    private void setArmed(boolean armed) {
        this.armed = armed;
        setButton(SLOT_CONFIRM, GuiButton.of(confirmIcon(), event -> onConfirmClick((Player) event.getWhoClicked())));
    }

    private ItemStack confirmIcon() {
        ItemStack stack = EnchantIcons.resolveCustom(armed ? "yeowool_enhance:enhance_confirm" : "yeowool_enhance:enhance_unconfirm");
        if (stack == null) {
            stack = new ItemStack(armed ? Material.LIME_DYE : Material.GRAY_DYE);
        }
        return stack;
    }
}
