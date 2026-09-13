package com.yeowool.community.settings;

import com.yeowool.community.ambience.LobbyBgmListener;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /내설정} — opens {@link PlayerSettingsGui}. */
public final class PlayerSettingsCommand implements CommandExecutor {

    private final MessageService messages;
    private final LobbyBgmListener lobbyBgm;

    public PlayerSettingsCommand(MessageService messages, LobbyBgmListener lobbyBgm) {
        this.messages = messages;
        this.lobbyBgm = lobbyBgm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new PlayerSettingsGui(player, lobbyBgm).open(player);
        return true;
    }
}
