package com.yeowool.community.settings;

import com.yeowool.community.ambience.LobbyBgmListener;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /내설정} — a single hub for player-facing on/off preferences.
 * Currently just the lobby BGM toggle ({@code lobbyBgm} is null on any
 * server that doesn't have it enabled, so the row is skipped there); add
 * more toggle rows here as they come up rather than scattering separate
 * commands per setting.
 */
public final class PlayerSettingsGui extends YeowoolGui {

    public PlayerSettingsGui(Player player, LobbyBgmListener lobbyBgm) {
        super(9, Component.text("내 설정", NamedTextColor.DARK_AQUA));

        int slot = 0;
        if (lobbyBgm != null) {
            setButton(slot++, bgmToggleButton(player, lobbyBgm));
        }
    }

    private GuiButton bgmToggleButton(Player player, LobbyBgmListener lobbyBgm) {
        boolean enabled = lobbyBgm.isEnabled(player);
        ItemStack item = new ItemStack(enabled ? Material.NOTE_BLOCK : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("로비 배경음악", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add((enabled
                ? Component.text("현재: 켜짐", NamedTextColor.GREEN)
                : Component.text("현재: 꺼짐", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭해서 " + (enabled ? "끄기" : "켜기"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);

        return GuiButton.of(item, event -> {
            lobbyBgm.setEnabled(player, !enabled);
            new PlayerSettingsGui(player, lobbyBgm).open(player);
        });
    }
}
