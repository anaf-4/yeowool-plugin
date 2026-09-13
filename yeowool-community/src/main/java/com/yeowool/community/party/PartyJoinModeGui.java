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
 * Last step of {@code /파티 생성} — pick {@link PartyManager.JoinMode} and
 * actually create the party (the plain-args {@code /파티 <이름> <최대인원>
 * [자유가입|신청승인]} path skips straight here without the GUI).
 */
public final class PartyJoinModeGui extends YeowoolGui {

    public PartyJoinModeGui(PartyManager partyManager, MessageService messages, String partyName, int maxSize) {
        super(9, Component.text("파티 [" + partyName + "] 가입 방식 선택", NamedTextColor.GOLD));

        setButton(3, GuiButton.of(modeIcon(Material.OAK_DOOR, "자유 가입", "누구나 /파티 가입으로 바로 들어올 수 있습니다"),
                event -> createWith(partyManager, messages, partyName, maxSize, PartyManager.JoinMode.FREE, (Player) event.getWhoClicked())));
        setButton(5, GuiButton.of(modeIcon(Material.IRON_DOOR, "신청 승인", "가입 신청이 오면 리더가 /파티 수락으로 승인해야 합니다"),
                event -> createWith(partyManager, messages, partyName, maxSize, PartyManager.JoinMode.APPROVAL, (Player) event.getWhoClicked())));
    }

    private void createWith(PartyManager partyManager, MessageService messages, String partyName, int maxSize,
                             PartyManager.JoinMode joinMode, Player player) {
        partyManager.create(player.getUniqueId(), player.getName(), partyName, maxSize, joinMode).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(partyManager.plugin(), () -> {
                    player.closeInventory();
                    switch (outcome.result()) {
                        case OK -> messages.send(player, "party.create-success",
                                Placeholder.unparsed("name", partyName), Placeholder.unparsed("maxsize", String.valueOf(maxSize)));
                        case ALREADY_IN_PARTY -> messages.send(player, "party.already-in-party");
                        case NAME_TAKEN -> messages.send(player, "party.name-taken");
                        case INVALID_NAME -> messages.send(player, "party.invalid-name");
                        case INVALID_MAX_SIZE -> messages.send(player, "party.invalid-max-size",
                                Placeholder.unparsed("limit", String.valueOf(PartyManager.HUD_SLOT_LIMIT)));
                        case ERROR -> messages.send(player, "general.error");
                    }
                }));
    }

    private ItemStack modeIcon(Material material, String name, String description) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
