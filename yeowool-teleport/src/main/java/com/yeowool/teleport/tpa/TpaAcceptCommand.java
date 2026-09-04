package com.yeowool.teleport.tpa;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.teleport.TeleportService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /tpaccept} — for a {@code TO} request the requester moves to the accepter; for {@code HERE} the accepter moves to the requester. */
public final class TpaAcceptCommand implements CommandExecutor {

    private final TpaManager tpaManager;
    private final TeleportService teleportService;
    private final MessageService messages;

    public TpaAcceptCommand(TpaManager tpaManager, TeleportService teleportService, MessageService messages) {
        this.tpaManager = tpaManager;
        this.teleportService = teleportService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        var pending = tpaManager.pending(player.getUniqueId());
        if (pending.isEmpty()) {
            messages.send(player, "tpa.no-pending-request");
            return true;
        }
        tpaManager.clear(player.getUniqueId());

        Player requester = Bukkit.getPlayer(pending.get().requester());
        if (requester == null) {
            messages.send(player, "tpa.requester-offline");
            return true;
        }

        Player mover = pending.get().kind() == TpaManager.Kind.TO ? requester : player;
        Player stationary = mover == requester ? player : requester;

        long cooldown = teleportService.remainingCooldownSeconds(mover);
        if (cooldown > 0) {
            messages.send(mover, "general.cooldown", Placeholder.unparsed("seconds", String.valueOf(cooldown)));
            messages.send(player, "tpa.accepted-cooldown");
            return true;
        }

        messages.send(player, "tpa.accepted", Placeholder.unparsed("target", requester.getName()));
        messages.send(requester, "tpa.accepted-notify", Placeholder.unparsed("target", player.getName()));
        teleportService.requestTeleport(mover, stationary.getLocation());
        return true;
    }
}
