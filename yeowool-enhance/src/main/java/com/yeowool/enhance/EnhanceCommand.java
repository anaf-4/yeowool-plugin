package com.yeowool.enhance;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /강화} — opens {@link EnhanceGui}, which then operates on whatever is in the viewer's main hand. */
public final class EnhanceCommand implements CommandExecutor {

    private final EnhanceService service;
    private final MessageService messages;
    private final int backgroundOffsetPx;

    public EnhanceCommand(EnhanceService service, MessageService messages, int backgroundOffsetPx) {
        this.service = service;
        this.messages = messages;
        this.backgroundOffsetPx = backgroundOffsetPx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new EnhanceGui(service, messages, backgroundOffsetPx).open(player);
        return true;
    }
}
