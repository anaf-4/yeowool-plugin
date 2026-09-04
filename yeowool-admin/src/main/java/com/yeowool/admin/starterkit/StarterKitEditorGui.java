package com.yeowool.admin.starterkit;

import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /여울관리 기본템} — a plain 54-slot editable inventory (every slot
 * is a free-edit slot, see {@link YeowoolGui#setEditableSlot(int)}) mirroring
 * {@link StarterKitService}'s current kit. Whatever is left in it when the
 * admin closes the window becomes the new kit, items exactly as placed
 * (including anything customized via {@code /귀속}, {@code /기간제},
 * {@code /아이템이름}, {@code /아이템설명}).
 */
public final class StarterKitEditorGui extends YeowoolGui {

    private final MessageService messages;
    private final StarterKitService service;

    public StarterKitEditorGui(MessageService messages, StarterKitService service) {
        super(54, Component.text("기본템 설정 (닫으면 저장됩니다)", NamedTextColor.DARK_GREEN));
        this.messages = messages;
        this.service = service;

        for (int slot = 0; slot < 54; slot++) {
            setEditableSlot(slot);
        }
        service.currentKit().forEach(getInventory()::setItem);
    }

    @Override
    public void onClose(Player player) {
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>();
        ItemStack[] contents = getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null && !contents[slot].getType().isAir()) {
                snapshot.put(slot, contents[slot]);
            }
        }
        service.save(snapshot);
        messages.send(player, "admin.starterkit-save-success", Placeholder.unparsed("count", String.valueOf(snapshot.size())));
    }
}
