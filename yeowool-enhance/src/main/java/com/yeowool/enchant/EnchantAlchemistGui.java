package com.yeowool.enchant;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /인챈트강화}'s Alchemist screen (9x3, matching AE's own alchemist.yml)
 * — drop two same-tier enchant books into slots 3/5, a preview of the fused
 * result appears at 13, and clicking the confirm icon at 22 consumes both
 * and gives the fused book. Non-book items dropped into 3/5 are rejected.
 * Background {@code yeowool_enhance:alchemist_bg}.
 */
public final class EnchantAlchemistGui extends YeowoolGui {

    private static final int SLOT_INPUT_A = 3;
    private static final int SLOT_INPUT_B = 5;
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_CONFIRM = 22;

    private final EnchantService service;
    private final MessageService messages;
    private final Player viewer;
    private ItemStack pendingResult;

    public EnchantAlchemistGui(EnchantService service, MessageService messages, Player viewer) {
        super(27, EnchantBackgroundImages.title(service.config().backgroundOffsetPx(), "alchemist_bg",
                Component.text("알케미스트", NamedTextColor.LIGHT_PURPLE)));
        this.service = service;
        this.messages = messages;
        this.viewer = viewer;

        ItemStack filler = fillerIcon();
        for (int slot = 0; slot < 27; slot++) {
            if (slot != SLOT_INPUT_A && slot != SLOT_INPUT_B && slot != SLOT_PREVIEW && slot != SLOT_CONFIRM) {
                setButton(slot, GuiButton.display(filler));
            }
        }

        setEditableSlot(SLOT_INPUT_A);
        setEditableSlot(SLOT_INPUT_B);
        ItemStack confirmIcon = EnchantIcons.resolveCustom("yeowool_enhance:enhance_confirm");
        setButton(SLOT_CONFIRM, GuiButton.of(confirmIcon != null ? confirmIcon : new ItemStack(Material.LIME_DYE), event -> confirm((Player) event.getWhoClicked())));
        refreshPreview();
    }

    /** {@code yeowool_enhance:enhance_invisible} (AE's own air.png) — occupies every non-input/preview/confirm slot so nothing else can land there (shift-click, drag, etc). */
    private ItemStack fillerIcon() {
        ItemStack stack = EnchantIcons.resolveCustom("yeowool_enhance:enhance_invisible");
        return stack != null ? stack : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onEditableSlotChanged() {
        rejectNonBooks();
        refreshPreview();
    }

    @Override
    public void onClose(Player player) {
        returnInput(SLOT_INPUT_A);
        returnInput(SLOT_INPUT_B);
    }

    private void rejectNonBooks() {
        for (int slot : new int[]{SLOT_INPUT_A, SLOT_INPUT_B}) {
            ItemStack item = getInventory().getItem(slot);
            if (item != null && !service.items().isBook(item)) {
                getInventory().setItem(slot, null);
                service.items().give(viewer, item);
            }
        }
    }

    private void returnInput(int slot) {
        ItemStack item = getInventory().getItem(slot);
        if (item != null) {
            getInventory().setItem(slot, null);
            service.items().give(viewer, item);
        }
    }

    private void refreshPreview() {
        ItemStack a = getInventory().getItem(SLOT_INPUT_A);
        ItemStack b = getInventory().getItem(SLOT_INPUT_B);
        pendingResult = (a == null || b == null) ? null : service.previewFuse(a, b);
        getInventory().setItem(SLOT_PREVIEW, pendingResult);
    }

    private void confirm(Player player) {
        if (pendingResult == null) {
            messages.send(player, "inchant.alchemist-invalid-pair");
            return;
        }
        getInventory().setItem(SLOT_INPUT_A, null);
        getInventory().setItem(SLOT_INPUT_B, null);
        service.items().give(player, pendingResult);
        messages.send(player, "inchant.fuse-success");
        pendingResult = null;
        getInventory().setItem(SLOT_PREVIEW, null);
    }
}
