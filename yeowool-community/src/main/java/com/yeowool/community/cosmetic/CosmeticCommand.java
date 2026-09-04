package com.yeowool.community.cosmetic;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class CosmeticCommand implements CommandExecutor, TabCompleter {

    private final YeowoolCoreAPI core;
    private final CosmeticManager manager;

    public CosmeticCommand(YeowoolCoreAPI core, CosmeticManager manager) {
        this.core = core;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 2 && args[0].equals("해제")) {
            try {
                manager.unequip(player, CosmeticDefinition.Type.valueOf(args[1].toUpperCase()));
                player.sendMessage(Component.text("해제했습니다.", NamedTextColor.GREEN));
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("사용법: /코스메틱 해제 [particle|chat_color]", NamedTextColor.RED));
            }
            return true;
        }

        new CosmeticShopGui(core, manager).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("해제"), args[0]);
        }
        if (args.length == 2 && args[0].equals("해제")) {
            return TabCompletions.filter(List.of("particle", "chat_color"), args[1]);
        }
        return List.of();
    }
}
