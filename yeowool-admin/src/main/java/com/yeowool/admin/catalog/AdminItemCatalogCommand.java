package com.yeowool.admin.catalog;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminItemCatalogCommand implements CommandExecutor {

    private final MessageService messages;
    private final List<CatalogEntry> entries;

    public AdminItemCatalogCommand(MessageService messages, List<CatalogEntry> entries) {
        this.messages = messages;
        this.entries = entries;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (entries.isEmpty()) {
            messages.send(player, "catalog.empty");
            return true;
        }
        new AdminItemCatalogGui(messages, entries).open(player);
        return true;
    }
}
