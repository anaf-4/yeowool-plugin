package com.yeowool.teleport.portalcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /포탈코어} — Portal Core 아이템의 {@code ~onUse} 스킬이 호출하는 명령어. */
public final class PortalCoreCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        new PortalCoreGui(1).open(player);
        return true;
    }
}
