package com.yeowool.life.bag;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /가방} — opens {@link BagHubGui}. */
public final class BagCommand implements CommandExecutor {

    private final BagManager manager;
    private final MessageService messages;

    public BagCommand(BagManager manager, MessageService messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!manager.isLoaded(player.getUniqueId())) {
            messages.send(player, "bag.not-loaded-yet");
            return true;
        }
        new BagHubGui(manager, player).open(player);
        return true;
    }
}
