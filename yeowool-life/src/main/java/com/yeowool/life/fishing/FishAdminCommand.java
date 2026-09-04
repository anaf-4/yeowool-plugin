package com.yeowool.life.fishing;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /낚시관리 물고기} (yeowool.admin) — {@link FishAdminGui}를 열어 등록된 모든 물고기를 즉시 지급받을 수 있게 한다. */
public final class FishAdminCommand implements CommandExecutor {

    private final MessageService messages;
    private final List<FishRarity> rarities;
    private final int backgroundOffsetPx;

    public FishAdminCommand(MessageService messages, List<FishRarity> rarities, int backgroundOffsetPx) {
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
        if (args.length != 1 || !args[0].equals("물고기")) {
            player.sendMessage(Component.text("사용법: /낚시관리 물고기", NamedTextColor.RED));
            return true;
        }
        new FishAdminGui(rarities, 0, backgroundOffsetPx).open(player);
        return true;
    }
}
