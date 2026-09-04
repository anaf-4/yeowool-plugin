package com.yeowool.community.profile;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.community.title.TitleDefinition;
import com.yeowool.community.title.TitleManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /내정보} — a self-only snapshot ("내" = my), unlike {@code /프로필}
 * which can also look up other players. Covers everything {@code /프로필}
 * shows plus the two balances it omits (은행/캐시), so a player has one
 * command for "how am I doing right now" without needing {@code /돈} too.
 */
public final class MyInfoCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final MessageService messages;

    public MyInfoCommand(YeowoolCoreAPI core, TitleManager titleManager, MessageService messages) {
        this.core = core;
        this.titleManager = titleManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        long playtimeMinutes = data.getStatistic(PlaytimeTracker.STAT_KEY);
        String title = titleManager.equippedId(data)
                .flatMap(titleManager::find)
                .map(TitleDefinition::display)
                .orElse("-");

        messages.send(player, "myinfo.display",
                Placeholder.unparsed("name", player.getName()),
                Placeholder.unparsed("title", title),
                Placeholder.unparsed("on", String.format("%,d", data.getOnBalance())),
                Placeholder.unparsed("bank", String.format("%,d", data.getBankBalance())),
                Placeholder.unparsed("cash", String.format("%,d", data.getCashBalance())),
                Placeholder.unparsed("landlevel", String.valueOf(core.landStats().getLandLevel(player.getUniqueId()))),
                Placeholder.unparsed("playtime", String.valueOf(playtimeMinutes)));
        return true;
    }
}
