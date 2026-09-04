package com.yeowool.economy.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /내캐시} — quick self-check of the premium "캐시" balance, separate from {@code /돈}'s 온/은행 summary. */
public final class MyCashCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public MyCashCommand(YeowoolCoreAPI core, MessageService messages) {
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        long cash = core.economyData().getCashBalance(player.getUniqueId());
        messages.send(player, "cash.balance", Placeholder.unparsed("cash", String.format("%,d", cash)));
        return true;
    }
}
