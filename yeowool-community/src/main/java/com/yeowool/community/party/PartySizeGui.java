package com.yeowool.community.party;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Second step of {@code /파티 생성} (after {@link PartyCreateListener}'s anvil
 * captures the name) — pick the max size (2-{@link PartyManager#HUD_SLOT_LIMIT})
 * and actually create the party.
 */
public final class PartySizeGui extends YeowoolGui {

    private static final int[] SIZE_SLOTS = {2, 4, 6};

    public PartySizeGui(PartyManager partyManager, MessageService messages, String partyName) {
        super(9, Component.text("파티 [" + partyName + "] 최대 인원 선택", NamedTextColor.GOLD));

        for (int i = 0; i < SIZE_SLOTS.length; i++) {
            int size = i + 2;
            setButton(SIZE_SLOTS[i], GuiButton.of(sizeIcon(size), event -> {
                Player player = (Player) event.getWhoClicked();
                partyManager.create(player.getUniqueId(), player.getName(), partyName, size).thenAccept(outcome ->
                        Bukkit.getScheduler().runTask(partyManager.plugin(), () -> {
                            player.closeInventory();
                            switch (outcome.result()) {
                                case OK -> messages.send(player, "party.create-success",
                                        Placeholder.unparsed("name", partyName), Placeholder.unparsed("maxsize", String.valueOf(size)));
                                case ALREADY_IN_PARTY -> messages.send(player, "party.already-in-party");
                                case NAME_TAKEN -> messages.send(player, "party.name-taken");
                                case INVALID_NAME -> messages.send(player, "party.invalid-name");
                                case INVALID_MAX_SIZE -> messages.send(player, "party.invalid-max-size",
                                        Placeholder.unparsed("limit", String.valueOf(PartyManager.HUD_SLOT_LIMIT)));
                                case ERROR -> messages.send(player, "general.error");
                            }
                        }));
            }));
        }
    }

    private ItemStack sizeIcon(int size) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD, size);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(size + "명", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭하여 최대 " + size + "명으로 파티 생성", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
