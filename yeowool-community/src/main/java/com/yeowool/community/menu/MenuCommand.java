package com.yeowool.community.menu;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /메뉴} — opens {@link MenuHubGui}. Also reachable in-game via Shift+F, see {@link MenuKeybindListener}. */
public final class MenuCommand implements CommandExecutor {

    private final MenuContext ctx;
    private final MessageService messages;

    public MenuCommand(MenuContext ctx, MessageService messages) {
        this.ctx = ctx;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new MenuHubGui(ctx, player).open(player);
        return true;
    }
}
