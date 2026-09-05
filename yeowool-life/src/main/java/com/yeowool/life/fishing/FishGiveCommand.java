package com.yeowool.life.fishing;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /물고기지급} (yeowool.admin) — {@link FishAdminCommand}와 동일한 {@link FishAdminGui}를 인자 없이 바로 연다. */
public final class FishGiveCommand implements CommandExecutor {

    private final MessageService messages;
    private final List<FishRarity> rarities;
    private final int backgroundOffsetPx;

    public FishGiveCommand(MessageService messages, List<FishRarity> rarities, int backgroundOffsetPx) {
        this.messages = messages;
        this.rarities = rarities;
        this.backgroundOffsetPx = backgroundOffsetPx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("yeowool.admin")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        new FishAdminGui(player, rarities, 0, backgroundOffsetPx).open(player);
        return true;
    }
}
