package com.yeowool.teleport.rtp;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /rtp} — 무작위 순간이동 GUI를 연다. */
public final class RtpCommand implements CommandExecutor {

    private final RtpManager manager;
    private final RtpConfig config;
    private final MessageService messages;

    public RtpCommand(RtpManager manager, RtpConfig config, MessageService messages) {
        this.manager = manager;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!config.enabled()) {
            messages.send(player, "rtp.disabled-here");
            return true;
        }
        new RtpGui(manager, config, messages, player).open(player);
        return true;
    }
}
