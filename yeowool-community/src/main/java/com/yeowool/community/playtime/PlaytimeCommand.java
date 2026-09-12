package com.yeowool.community.playtime;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /플레이타임} — opens {@link PlaytimeGui} (1/6/12/24시간 milestone claim buttons). */
public final class PlaytimeCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final PlaytimeManager playtimeManager;
    private final MessageService messages;

    public PlaytimeCommand(YeowoolCoreAPI core, PlaytimeManager playtimeManager, MessageService messages) {
        this.core = core;
        this.playtimeManager = playtimeManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new PlaytimeGui(core, playtimeManager, messages, player).open(player);
        return true;
    }
}
