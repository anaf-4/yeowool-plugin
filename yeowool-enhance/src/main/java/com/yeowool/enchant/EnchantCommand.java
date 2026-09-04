package com.yeowool.enchant;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /인챈트강화} — opens {@link EnchantGui}. Fully separate system from {@code /강화}. */
public final class EnchantCommand implements CommandExecutor {

    private final EnchantService service;
    private final MessageService messages;

    public EnchantCommand(EnchantService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new EnchantGui(service, messages).open(player);
        return true;
    }
}
