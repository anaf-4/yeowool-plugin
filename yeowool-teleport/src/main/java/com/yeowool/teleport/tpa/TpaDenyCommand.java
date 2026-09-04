package com.yeowool.teleport.tpa;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /tpdeny} — declines the pending request addressed to the sender. */
public final class TpaDenyCommand implements CommandExecutor {

    private final TpaManager tpaManager;
    private final MessageService messages;

    public TpaDenyCommand(TpaManager tpaManager, MessageService messages) {
        this.tpaManager = tpaManager;
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
        messages.send(player, "tpa.denied");

        Player requester = Bukkit.getPlayer(pending.get().requester());
        if (requester != null) {
            messages.send(requester, "tpa.denied-notify", Placeholder.unparsed("target", player.getName()));
        }
        return true;
    }
}
