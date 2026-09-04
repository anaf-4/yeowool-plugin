package com.yeowool.analytics.season;

import com.yeowool.analytics.MetricsCollector;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;

/**
 * {@code /시즌랭킹} — current standings (read-only, anyone). {@code /시즌랭킹 종료}
 * force-ends the season right now instead of waiting for the configured
 * period — {@code yeowool.analytics.season.manage} only, mainly for testing
 * reward tiers without waiting a full week.
 */
public final class SeasonRankingCommand implements CommandExecutor {

    private static final String MANAGE_PERMISSION = "yeowool.analytics.season.manage";
    private static final int TOP_N = 10;

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MetricsCollector collector;
    private final SeasonManager seasonManager;
    private final ExecutorService executor;

    public SeasonRankingCommand(JavaPlugin plugin, MessageService messages, MetricsCollector collector, SeasonManager seasonManager, ExecutorService executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.collector = collector;
        this.seasonManager = seasonManager;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equals("종료")) {
            if (!sender.hasPermission(MANAGE_PERMISSION)) {
                messages.send(sender, "season.no-permission");
                return true;
            }
            messages.send(sender, "season.ending");
            seasonManager.runNow();
            return true;
        }

        messages.send(sender, "season.collecting");
        executor.execute(() -> {
            try {
                var rows = collector.topSeasonScore(TOP_N);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(sender, "season.header", Placeholder.unparsed("top_n", String.valueOf(TOP_N)));
                    if (rows.isEmpty()) {
                        messages.send(sender, "general.no-data");
                        return;
                    }
                    int rank = 1;
                    for (var row : rows) {
                        messages.send(sender, "season.entry",
                                Placeholder.unparsed("rank", String.valueOf(rank)),
                                Placeholder.unparsed("name", row.username()),
                                Placeholder.unparsed("value", String.format("%,d", row.value())));
                        rank++;
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("시즌 랭킹 조회 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "season.load-failed"));
            }
        });
        return true;
    }
}
