package com.yeowool.community.ranking;

import com.yeowool.community.ranking.statue.RankingStatueManager;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /명예의전당 [돈|마을|접속시간]} — top-10 chat view.
 * {@code /명예의전당 동상설정 <돈|마을|접속시간> <1|2|3>} (관리자) — registers a
 * Citizens statue slot at the sender's current location; only present when
 * {@link #statueManager} is non-null (Citizens installed).
 */
public final class RankingCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final RankingManager rankingManager;
    private final RankingStatueManager statueManager;

    public RankingCommand(MessageService messages, RankingManager rankingManager, RankingStatueManager statueManager) {
        this.messages = messages;
        this.rankingManager = rankingManager;
        this.statueManager = statueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equals("동상설정")) {
            setStatue(sender, args);
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "ranking.usage");
            return true;
        }
        RankingCategory category = RankingCategory.byCommandKeyword(args[0]);
        if (category == null) {
            messages.send(sender, "ranking.usage");
            return true;
        }
        show(sender, category);
        return true;
    }

    private void show(CommandSender sender, RankingCategory category) {
        var top = rankingManager.top(category);
        if (top.isEmpty()) {
            messages.send(sender, "ranking.empty", Placeholder.unparsed("category", category.commandKeyword()));
            return;
        }
        sender.sendMessage(Component.text("=== " + category.commandKeyword() + " 순위 TOP " + top.size() + " ===", NamedTextColor.GOLD));
        for (int i = 0; i < top.size(); i++) {
            var entry = top.get(i);
            sender.sendMessage(Component.text((i + 1) + "위  ", NamedTextColor.YELLOW)
                    .append(Component.text(entry.username(), NamedTextColor.WHITE))
                    .append(Component.text("  " + formatValue(category, entry.value()), NamedTextColor.GRAY)));
        }
    }

    private String formatValue(RankingCategory category, long value) {
        return switch (category) {
            case MONEY -> String.format("%,d온", value);
            case LAND -> "Lv." + value;
            case PLAYTIME -> DurationFormat.humanize(value * 60_000L);
        };
    }

    private void setStatue(CommandSender sender, String[] args) {
        if (statueManager == null) {
            messages.send(sender, "ranking.statue-unavailable");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!sender.hasPermission("yeowool.community.ranking.manage")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length != 3) {
            messages.send(sender, "ranking.statue-usage");
            return;
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
            return;
        }
        statueManager.setLocation(category, position, player.getLocation());
        messages.send(sender, "ranking.statue-success",
                Placeholder.unparsed("category", category.commandKeyword()),
                Placeholder.unparsed("position", String.valueOf(position)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(List.of("돈", "마을", "접속시간"));
            if (statueManager != null) {
                options.add("동상설정");
            }
            return TabCompletions.filter(options, args[0]);
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
