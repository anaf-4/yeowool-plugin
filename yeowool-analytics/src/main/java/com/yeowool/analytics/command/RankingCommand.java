package com.yeowool.analytics.command;

import com.yeowool.analytics.MetricsCollector;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * {@code /랭킹 [온|은행|토지레벨|낚시도감]} — top 10 for a chosen stat. A
 * companion to {@code /여울통계}'s aggregate report: this is per-player
 * instead of server-wide totals.
 */
public final class RankingCommand implements CommandExecutor, TabCompleter {

    private static final List<String> CATEGORIES = List.of("온", "은행", "토지레벨", "낚시도감");
    private static final int TOP_N = 10;

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MetricsCollector collector;
    private final ExecutorService executor;

    public RankingCommand(JavaPlugin plugin, MessageService messages, MetricsCollector collector, ExecutorService executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.collector = collector;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String category = args.length >= 1 ? args[0] : "온";
        if (!CATEGORIES.contains(category)) {
            messages.send(sender, "ranking.usage");
            return true;
        }

        messages.send(sender, "ranking.collecting");
        executor.execute(() -> {
            try {
                var rows = switch (category) {
                    case "은행" -> collector.topBankBalance(TOP_N);
                    case "토지레벨" -> collector.topLandLevel(TOP_N);
                    case "낚시도감" -> collector.topFishingCatalog(TOP_N);
                    default -> collector.topOnBalance(TOP_N);
                };
                String unit = switch (category) {
                    case "토지레벨" -> "Lv.";
                    case "낚시도감" -> "종";
                    default -> "온";
                };
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(sender, "ranking.header",
                            Placeholder.unparsed("category", category),
                            Placeholder.unparsed("top_n", String.valueOf(TOP_N)));
                    if (rows.isEmpty()) {
                        messages.send(sender, "general.no-data");
                        return;
                    }
                    int rank = 1;
                    for (var row : rows) {
                        messages.send(sender, "ranking.entry",
                                Placeholder.unparsed("rank", String.valueOf(rank)),
                                Placeholder.unparsed("name", row.username()),
                                Placeholder.unparsed("value", String.format("%,d", row.value())),
                                Placeholder.unparsed("unit", unit));
                        rank++;
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("랭킹 조회 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "ranking.load-failed"));
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(CATEGORIES, args[0]);
        }
        return List.of();
    }
}
