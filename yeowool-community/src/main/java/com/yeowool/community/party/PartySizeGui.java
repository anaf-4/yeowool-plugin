package com.yeowool.community.party;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Second step of {@code /파티 생성} (after {@link PartyCreateListener}'s anvil
 * captures the name) — pick the max size (2-{@link PartyManager#HUD_SLOT_LIMIT}),
 * then {@link PartyJoinModeGui} picks the join mode and actually creates it.
 */
public final class PartySizeGui extends YeowoolGui {

    private static final int[] SIZE_SLOTS = {2, 4, 6};

    public PartySizeGui(PartyManager partyManager, MessageService messages, String partyName) {
        super(9, Component.text("파티 [" + partyName + "] 최대 인원 선택", NamedTextColor.GOLD));

        for (int i = 0; i < SIZE_SLOTS.length; i++) {
            int size = i + 2;
            setButton(SIZE_SLOTS[i], GuiButton.of(sizeIcon(size), event -> {
                Player player = (Player) event.getWhoClicked();
                new PartyJoinModeGui(partyManager, messages, partyName, size).open(player);
            }));
        }
    }

    private ItemStack sizeIcon(int size) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD, size);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(size + "명", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭하여 최대 " + size + "명으로 진행", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
