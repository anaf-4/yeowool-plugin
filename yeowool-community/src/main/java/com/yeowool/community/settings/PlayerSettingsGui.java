package com.yeowool.community.settings;

import com.yeowool.community.ambience.LobbyBgmListener;
import com.yeowool.core.api.YeowoolCoreAPI;
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
 * The lobby BGM row is skipped on any server that doesn't have it enabled
 * ({@code lobbyBgm} is null there); the block-toggle rows (whisper/trade)
 * just flip a {@code PlayerData} setting the sending side already checks —
 * see {@link com.yeowool.community.chat.WhisperCommand#sendWhisper} and
 * {@code com.yeowool.market.trade.TradeManager#sendRequest}. Add more
 * toggle rows here as they come up rather than scattering separate
 * commands per setting.
 */
public final class PlayerSettingsGui extends YeowoolGui {

    public static final String BLOCK_WHISPER_KEY = "block.whisper";
    public static final String BLOCK_TRADE_KEY = "block.trade";

    private final YeowoolCoreAPI core;
    private final LobbyBgmListener lobbyBgm;

    public PlayerSettingsGui(Player player, YeowoolCoreAPI core, LobbyBgmListener lobbyBgm) {
        super(9, Component.text("내 설정", NamedTextColor.DARK_AQUA));
        this.core = core;
        this.lobbyBgm = lobbyBgm;

        int slot = 0;
        if (lobbyBgm != null) {
            setButton(slot++, bgmToggleButton(player));
        }
        setButton(slot++, blockToggleButton(player, BLOCK_WHISPER_KEY, "귓속말 차단"));
        setButton(slot++, blockToggleButton(player, BLOCK_TRADE_KEY, "거래 요청 차단"));
    }

    private GuiButton bgmToggleButton(Player player) {
        boolean enabled = lobbyBgm.isEnabled(player);
        ItemStack item = toggleItem("로비 배경음악", enabled, "켜짐", "꺼짐");
        return GuiButton.of(item, event -> {
            lobbyBgm.setEnabled(player, !enabled);
            new PlayerSettingsGui(player, core, lobbyBgm).open(player);
        });
    }

    private GuiButton blockToggleButton(Player player, String settingKey, String label) {
        boolean blocked = "true".equals(core.playerData().getOnline(player.getUniqueId()).getSetting(settingKey, "false"));
        ItemStack item = toggleItem(label, blocked, "차단함", "차단 안 함");
        return GuiButton.of(item, event -> {
            core.playerData().getOnline(player.getUniqueId()).setSetting(settingKey, String.valueOf(!blocked));
            new PlayerSettingsGui(player, core, lobbyBgm).open(player);
        });
    }

    private ItemStack toggleItem(String label, boolean on, String onText, String offText) {
        ItemStack item = new ItemStack(on ? Material.NOTE_BLOCK : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add((on
                ? Component.text("현재: " + onText, NamedTextColor.GREEN)
                : Component.text("현재: " + offText, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭해서 전환", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
