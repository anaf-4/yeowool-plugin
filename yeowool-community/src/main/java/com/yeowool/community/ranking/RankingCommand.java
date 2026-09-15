package com.yeowool.community.ranking;

import com.yeowool.community.ranking.statue.RankingStatueManager;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /명예의전당 동상설정 <돈|마을|접속시간> <1|2|3>} (관리자) — registers a
 * Citizens statue slot at the sender's current location; only present when
 * {@link #statueManager} is non-null (Citizens installed). The leaderboard
 * itself is read via the {@code %yeowool_rank_...%} placeholders, not a
 * chat command.
 */
public final class RankingCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final RankingStatueManager statueManager;

    public RankingCommand(MessageService messages, RankingStatueManager statueManager) {
        this.messages = messages;
        this.statueManager = statueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (statueManager == null) {
            messages.send(sender, "ranking.statue-unavailable");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission("yeowool.community.ranking.manage")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length != 3 || !args[0].equals("동상설정")) {
            messages.send(sender, "ranking.statue-usage");
            return true;
        }
        RankingCategory category = RankingCategory.byCommandKeyword(args[1]);
        int position;
        try {
            position = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            category = null;
            position = -1;
        }
        if (category == null || position < 1 || position > 3) {
            messages.send(sender, "ranking.statue-usage");
            return true;
        }
        statueManager.setLocation(category, position, player.getLocation());
        messages.send(sender, "ranking.statue-success",
                Placeholder.unparsed("category", category.commandKeyword()),
                Placeholder.unparsed("position", String.valueOf(position)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("동상설정"), args[0]);
        }
        if (args.length == 2 && args[0].equals("동상설정")) {
            return TabCompletions.filter(List.of("돈", "마을", "접속시간"), args[1]);
        }
        if (args.length == 3 && args[0].equals("동상설정")) {
            return TabCompletions.filter(List.of("1", "2", "3"), args[2]);
        }
        return List.of();
    }
}
